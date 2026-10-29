"""R04b evidence/source/artifact audit. Read-only except the ignored audit report.

Does not close a backlog item or substitute for actual receiver DB/mTLS tests.
Canonical Identity provider, Learning, coordinator and release remain outside R04b.
"""
from __future__ import annotations
import argparse, ast, collections, hashlib, json, re, socket, zipfile
from pathlib import Path
from urllib.parse import unquote
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
E=ROOT/'artifacts/quality/R04b'
JAR=ROOT/'backend/services/content-service/target/content-service-0.1.0-SNAPSHOT.jar'

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path): return json.loads(path.read_text(encoding='utf-8-sig'))
def closed(port):
    with socket.socket() as s:
        s.settimeout(1)
        assert s.connect_ex(('127.0.0.1',port))!=0, ('listener open',port)
def entries(path):
    with zipfile.ZipFile(path) as z:
        return {n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist()}
def walk(node):
    for child in node.get('children',[]): yield child; yield from walk(child)

def verify_run(relative,task,case,required,minimum,layer):
    run=(ROOT/relative).resolve();assert run.is_relative_to(E) and run!=E
    report=read(run/'report.json')
    assert report['status']=='PASS' and report['clusterStopped']
    assert (report['service'],report['task'],report['case'])==('content',task,case)
    closed(report['port']);assert len(report['services'])==1
    own=report['services'][0];assert own['status']=='PASS'
    for folder,expected,count,label in [('surefire-reports',{'ContentBootstrapConfigTests','ContentBootstrapHttpTests','identityaccess.AdmissionWireTests'},52,'bootstrap'),('failsafe-reports',required,minimum,layer)]:
        files=list((run/'content'/folder).glob('TEST-*.xml'))
        assert {p.name.removeprefix('TEST-lab.backend.content.').removesuffix('.xml') for p in files}==expected
        total=collections.Counter()
        for file in files:
            suite=ET.parse(file).getroot();assert int(suite.attrib['tests'])>0
            assert len(suite.findall('testcase'))==int(suite.attrib['tests'])
            for key in ['tests','failures','errors','skipped']:total[key]+=int(suite.attrib[key])
        assert dict(total)==own['reports'][label] and total['tests']>=count
        assert total['failures']==total['errors']==total['skipped']==0
    for path,digest in read(run/'content-inputs.json').items():
        assert sha(ROOT/path)==digest,('qualified input changed',path)
    artifact=run/'content/qualified-artifact.jar'
    assert sha(artifact)==own['artifactSha256'] and entries(artifact)==entries(JAR)
    return {'run':run.relative_to(ROOT).as_posix(),'artifactSha256':sha(artifact),
            'retainedArtifact':artifact.relative_to(ROOT).as_posix(),'reports':own['reports'],
            'inputHashes':(run/'content-inputs.json').relative_to(ROOT).as_posix()}

def main():
    parser=argparse.ArgumentParser()
    for item in ['db','contract','sec02','sec04','sec09','sec15']:parser.add_argument('--'+item+'-run',required=True)
    parser.add_argument('--state',choices=['IN_PROGRESS','DONE'],default='IN_PROGRESS')
    args=parser.parse_args()
    db_suites={'database.DatabaseIsolationIT','identityaccess.AdmissionGateIT'}
    runtime={'identityaccess.AdmissionRuntimeIT'}
    evidence={
        'db':verify_run(args.db_run,'db',None,db_suites,37,'database'),
        'contract':verify_run(args.contract_run,'contract',None,runtime|{'TransportTrustIT','WireContractIT'},95,'transport'),
        'sec02':verify_run(args.sec02_run,'fault','SEC-02',runtime,1,'transport'),
        'sec04':verify_run(args.sec04_run,'fault','SEC-04',db_suites,37,'database'),
        'sec09':verify_run(args.sec09_run,'fault','SEC-09',runtime,2,'transport'),
        'sec15':verify_run(args.sec15_run,'fault','SEC-15',runtime|{'TransportTrustIT'},17,'transport')}
    assert sha(JAR)==evidence['contract']['artifactSha256'], 'Use the exact retained TLS artifact for final smoke/candidate'
    smoke=read(E/'artifact-content/artifact-smoke.json')
    assert smoke['artifactSha256']==sha(JAR) and len(smoke['cases'])==7 and all(c['status']=='PASS' for c in smoke['cases'])
    for item in smoke['cases']:
        if 'port' in item:closed(item['port'])
    tree_path=E/'content-dependency-tree.json';resolved=[];seen=set();cache={}
    for item in walk(read(tree_path)):
        key=(item['groupId'],item['artifactId'],item['type'],item.get('classifier',''),item['version'],item.get('scope','compile'))
        if key in seen:continue
        seen.add(key);group,aid,typ,classifier,version,scope=key
        filename=aid+'-'+version+('-'+classifier if classifier else '')+'.'+typ
        path=ROOT/'.tools/m2/repository'/group.replace('.','/')/aid/version/filename
        digest=sha(path);cache[filename]=digest
        resolved.append({'coordinate':f'{group}:{aid}:{typ}:{classifier}:{version}','scope':scope,'sha256':digest})
    resolved.sort(key=lambda a:(a['coordinate'],a['scope']))
    assert dict(collections.Counter(a['scope'] for a in resolved))=={'compile':40,'runtime':1,'test':31}
    loader=ROOT/'.tools/m2/repository/org/springframework/boot/spring-boot-loader-tools/4.1.1/spring-boot-loader-tools-4.1.1.jar'
    with zipfile.ZipFile(loader) as z:injected=hashlib.sha256(z.read('META-INF/jarmode/spring-boot-jarmode-tools.jar')).hexdigest()
    cache['spring-boot-jarmode-tools-4.1.1.jar']=injected
    with zipfile.ZipFile(JAR) as z:
        names=z.namelist();libs=[n for n in names if n.startswith('BOOT-INF/lib/') and n.endswith('.jar')]
        assert len(libs)==36
        for lib in libs:assert hashlib.sha256(z.read(lib)).hexdigest()==cache[Path(lib).name],('runtime library mismatch',lib)
        assert not any(re.search(r'flyway|junit|mockito',n,re.I) for n in libs)
        assert not any(re.search(r'\.p12$|db-test-migration|db-failure|db-recovery|AdmissionGateIT',n) for n in names)
        assert z.read('BOOT-INF/classes/db/migration/V2__admission_receiver.sql')==(ROOT/'backend/services/content-service/src/main/resources/db/migration/V2__admission_receiver.sql').read_bytes()
    markdown=list(ROOT.glob('*.md'))+list((ROOT/'docs').rglob('*.md'))
    for folder in ['backend','ops','contracts']:markdown+=list((ROOT/folder).rglob('README.md'))
    markdown=[p for p in markdown if 'target' not in p.parts];links=0
    for path in markdown:
        body=path.read_text(encoding='utf-8');assert '\ufffd' not in body and len(re.findall(r'^```',body,re.M))%2==0,path
        for dest in re.findall(r'\[[^\]]*\]\(([^)]+)\)',body):
            dest=dest.strip().strip('<>')
            if re.match(r'^(https?://|mailto:|codex:|plugin:|app:|#)',dest):continue
            dest=unquote(dest.split('#')[0].split('?')[0])
            if dest:assert (path.parent/dest).exists(),(path,dest);links+=1
    rows={}
    for line in (ROOT/'docs/delivery-backlog.md').read_text(encoding='utf-8').splitlines():
        if not re.match(r'^\| BL-R\d{2}[a-z]? · ',line):continue
        cols=[c.strip() for c in line.split('|')];task,state=re.fullmatch(r'(BL-R\d{2}[a-z]?) · (\w+)',cols[1]).groups()
        assert task not in rows;rows[task]=(state,re.findall(r'BL-R\d{2}[a-z]?',cols[3]))
    assert len(rows)==73 and sum(len(d) for _,d in rows.values())==169
    done={'BL-R00','BL-R01','BL-R01a','BL-R01b','BL-R01c','BL-R01d','BL-R02a','BL-R02b','BL-R03','BL-R04a'}
    for task,(state,deps) in rows.items():
        assert state==(args.state if task=='BL-R04b' else 'DONE' if task in done else 'PLANNED'),(task,state)
        assert all(dep in rows for dep in deps)
    active,visited=set(),set()
    def dag(task):
        assert task not in active,task
        if task in visited:return
        active.add(task)
        for dep in rows[task][1]:dag(dep)
        active.remove(task);visited.add(task)
    for task in rows:dag(task)
    unchanged=0
    # Latest qualified Identity, historical unchanged Learning; helper changes are scoped here.
    for source,svc in [('artifacts/quality/R04a/b033ae94-e719-46b7-9de8-fca1b512a9b4','identity'),('artifacts/quality/R02b/5f9a3d8f-3045-4803-9ac5-3fc6aec1d483','learning')]:
        for path,digest in read(ROOT/source/(svc+'-inputs.json')).items():
            normalized=path.replace('\\','/')
            if normalized.startswith('backend/services/'+svc+'/') or normalized=='backend/pom.xml':
                assert sha(ROOT/path)==digest,('peer source changed',path);unchanged+=1
    for path,digest in read(ROOT/'artifacts/quality/R02b/baseline.json').items():
        if path in ['frontend/package.json','frontend/package-lock.json']:assert sha(ROOT/path)==digest;unchanged+=1
    for source in (ROOT/'backend/services/content-service/src/main').rglob('*.java'):
        body=source.read_text(encoding='utf-8')
        assert 'lab.backend.identity' not in body and 'lab.backend.learning' not in body,source
    helper=read(E/'helper-tests.json');assert len(helper['cases'])>=79 and all(c['result']=='PASS' for c in helper['cases'])
    guards=(E/'lab-guards.log').read_text(encoding='utf-8-sig');assert 'Ran 8 tests' in guards and 'OK' in guards
    for run in E.glob('*/report.json'):
        report=read(run)
        if 'clusterStopped' in report:assert report['clusterStopped'];closed(report['port'])
    for port in [8080,8443,18081,18082,18083]:closed(port)
    secrets=[]
    for path in (ROOT/'.tools/db-lab').glob('run-*/test-credentials.properties'):
        secrets += [l.split('=',1)[1] for l in path.read_text(encoding='utf-8').splitlines() if '.password=' in l]
    for path in (ROOT/'.tools/trust-lab').glob('run-*/*.properties'):
        secrets += [l.split('=',1)[1].replace(r'\=','=') for l in path.read_text(encoding='utf-8').splitlines() if l.startswith(('storePassword=','dbPassword=','throttleKey='))]
    forbidden={s.encode('ascii') for s in secrets if len(s)>=32 and len(set(s))>1}
    scan=markdown+list((ROOT/'ops').rglob('*.json'))+list((ROOT/'scripts').rglob('*.py'))+list((ROOT/'scripts').rglob('*.ps1'))
    scan+=list((ROOT/'backend/services').rglob('*.java'))+list((ROOT/'backend/services').rglob('*.sql'))
    for proof in evidence.values():
        directory=ROOT/proof['run'];scan += [p for p in directory.rglob('*') if p.is_file() and p.suffix in {'.log','.xml','.json'}]
        if (directory/'pki-manifest.json').is_file():
            trust=(ROOT/read(directory/'pki-manifest.json')['directory']).resolve();assert trust.is_relative_to(ROOT/'.tools/trust-lab')
            scan+=list(trust.rglob('*.log'))
    for path in scan:
        if 'target' in path.parts:continue
        data=path.read_bytes();assert not any(secret in data for secret in forbidden),('generated secret leaked',path)
    for path in ['scripts/db_lab.py','scripts/trust_lab.py','scripts/tests/content_admission_review.py']:ast.parse((ROOT/path).read_text(encoding='utf-8'))
    result={'scope':'R04b Content receiver only; controlled provider fixture, not canonical Identity/end-to-end revoke/release',
            'state':args.state,'status':'PASS','artifactSha256':sha(JAR),'evidence':evidence,
            'dependencyTreeSha256':sha(tree_path),'artifacts':resolved,'runtimeLibraries':len(libs),
            'packagerInjectedRuntime':{'sha256':injected,'sourceCacheSha256':sha(loader)},
            'markdown':len(markdown),'localLinks':links,'taskIds':len(rows),'dagEdges':169,'unchangedPeerAndFeInputs':unchanged,'helperCases':len(helper['cases'])}
    (E/'receiver-review.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k not in {'artifacts','evidence'}},indent=2))

if __name__=='__main__':main()
