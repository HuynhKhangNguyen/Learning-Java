"""FX-03 lab PKI generator; no system trust installation or production keys."""
from __future__ import annotations
import json
import os
from pathlib import Path
import secrets
import tempfile
import uuid
import db_lab

ROOT=Path(__file__).resolve().parents[1]

def create_bundle(evidence, credentials):
    parent=ROOT/'.tools/trust-lab'
    if parent.resolve()!=parent:raise RuntimeError('Trust lab root resolves through a link')
    parent.mkdir(parents=True,exist_ok=True)
    directory=Path(tempfile.mkdtemp(prefix='run-',dir=parent)).resolve()
    run_id=str(uuid.uuid4())
    (directory/'ownership.json').write_text(json.dumps({'schemaVersion':1,'workspace':str(ROOT),'runId':run_id}),encoding='utf-8')
    manifest=json.loads((ROOT/'ops/release/dependencies.json').read_text(encoding='utf-8'))
    keytool=ROOT/manifest['toolchain']['jdk']['installDirectory']/'bin/keytool.exe'
    password=secrets.token_hex(24)
    environment=dict(os.environ);environment['BACKEND_LAB_STORE_PASS']=password
    log=evidence/'keytool.log';log.parent.mkdir(parents=True,exist_ok=True)
    step=0
    def key(*arguments):
        nonlocal step
        step+=1
        output=directory/f'keytool-{step}.log'
        db_lab.run([str(keytool),*arguments,'-storepass:env','BACKEND_LAB_STORE_PASS'],env=environment,log=output,timeout=30)
        with log.open('a',encoding='utf-8') as target:target.write(output.read_text(encoding='utf-8'))
    for ca in ['ca','other-ca']:
        key('-genkeypair','-alias','ca','-keyalg','EC','-groupname','secp256r1','-dname',f'CN=Backend Lab Test {ca}',
            '-validity','30','-ext','bc=ca:true','-ext','ku=keyCertSign,cRLSign','-keystore',str(directory/(ca+'.p12')),'-storetype','PKCS12')
        key('-exportcert','-alias','ca','-keystore',str(directory/(ca+'.p12')),'-file',str(directory/(ca+'.crt')))
    key('-importcert','-noprompt','-alias','ca','-file',str(directory/'ca.crt'),'-keystore',str(directory/'trust.p12'),'-storetype','PKCS12')
    variants={name:(name,'lab','ca','serverAuth,clientAuth',False,False) for name in ['identity','content','learning','edge','operator']}
    variants.update({'wrong-san':('unregistered','lab','ca','clientAuth',False,False),
                     'wrong-env':('content','pilot','ca','clientAuth',False,False),
                     'wrong-ca':('content','lab','other-ca','clientAuth',False,False),
                     'wrong-eku':('content','lab','ca','serverAuth',False,False),
                     'expired':('content','lab','ca','clientAuth',True,False),
                     'ambiguous':('content','lab','ca','clientAuth',False,True)})
    for name,(identity,env,issuer,eku,expired,ambiguous) in variants.items():
        store=directory/(name+'.p12');csr=directory/(name+'.csr');cert=directory/(name+'.crt')
        key('-genkeypair','-alias','leaf','-keyalg','EC','-groupname','secp256r1','-dname',f'CN=Test {name}','-validity','30','-keystore',str(store),'-storetype','PKCS12')
        key('-certreq','-alias','leaf','-keystore',str(store),'-file',str(csr))
        san=f'uri:spiffe://backend-lab/{env}/{identity},dns:localhost,ip:127.0.0.1'
        if ambiguous:san+=',uri:spiffe://backend-lab/lab/identity'
        options=['-gencert','-alias','ca','-keystore',str(directory/(issuer+'.p12')),'-infile',str(csr),'-outfile',str(cert),'-validity','1' if expired else '20','-ext','bc=ca:false','-ext','ku=digitalSignature','-ext','EKU='+eku,'-ext','SAN='+san]
        if expired:options+=['-startdate','-2d']
        key(*options)
        key('-importcert','-noprompt','-alias','ca','-file',str(directory/(issuer+'.crt')),'-keystore',str(store))
        key('-importcert','-noprompt','-alias','leaf','-file',str(cert),'-keystore',str(store))
    # The completed runtime fixture retains public CA certificates only. Root
    # signing keys are never available when an application starts.
    for name in ['ca.p12','other-ca.p12']:
        target=(directory/name).resolve()
        if target.parent!=directory or directory.parent!=parent.resolve():
            raise RuntimeError('Signing-key removal target escaped owned test fixture')
        target.unlink()
    configs={}
    realm=str(uuid.uuid4())
    for service in ['identity','content','learning']:
        values={'schemaVersion':'1','service':service,'environment':'lab','runId':run_id,'realm':realm,'generation':str(uuid.uuid4()),
                'keyStore':service+'.p12','trustStore':'trust.p12','storePassword':password,'dbPort':credentials['lab.port'],
                'dbPassword':credentials[service+'.app.password'],'dbRunId':credentials['lab.runId'],'test.clockOffsetMs':'0'}
        path=directory/(service+'.properties');db_lab.write_properties(path,values);configs[service]=path
    (evidence/'pki-manifest.json').write_text(json.dumps({'scope':'FX-03 generated test-only CA/leaf fixtures; no OS trust or production certificate qualification',
        'runId':run_id,'directory':str(directory.relative_to(ROOT)),'generator':'pinned JDK21 keytool','variants':list(variants),'serviceConfigs':{k:str(v.relative_to(ROOT)) for k,v in configs.items()}},indent=2),encoding='utf-8')
    return configs
