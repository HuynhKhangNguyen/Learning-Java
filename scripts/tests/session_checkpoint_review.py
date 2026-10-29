"""R04a DB/opt-in runtime leaf evidence audit, never a release gate.

--write-candidate mechanically records resolved tree/hashes and a separately
labelled candidate. --state DONE audits an already closed R04a leaf only;
it does not close the registry or authorize any sibling. R03 history is retained.
"""
from __future__ import annotations
import argparse, ast, collections, hashlib, json, re, socket, zipfile
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import unquote
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
E=ROOT/'artifacts/quality/R04a'
MANIFEST=ROOT/'ops/release/dependencies.json'
JAR=ROOT/'backend/services/identity-service/target/identity-service-0.1.0-SNAPSHOT.jar'

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def closed(port):
    with socket.socket() as s:assert s.connect_ex(('127.0.0.1',port))!=0,('listener open',port)
def content(path):
    digest=hashlib.sha256()
    with zipfile.ZipFile(path) as archive:
        for name in sorted(archive.namelist()):
            encoded=name.encode();digest.update(len(encoded).to_bytes(4,'big'));digest.update(encoded)
            digest.update(hashlib.sha256(archive.read(name)).digest())
    return digest.hexdigest()
def run(relative,task,expected_suites,expected_count,layer):
    directory=(ROOT/relative).resolve();assert directory.is_relative_to(E) and directory!=E
    report=read(directory/'report.json')
    assert report['task']==task and report['case'] is None and report['service']=='identity'
    assert report['status']=='PASS' and report['clusterStopped'];closed(report['port'])
    assert len(report['services'])==1;own=report['services'][0];assert own['status']=='PASS'
    for folder,required,count,label in [('surefire-reports',None,63,'bootstrap'),('failsafe-reports',expected_suites,expected_count,layer)]:
        files=list((directory/'identity'/folder).glob('TEST-*.xml'));assert files
        if required is not None:assert {p.name for p in files}==set(required)
        else:assert 'TEST-lab.backend.identity.session.SessionHttpHandlerTests.xml' in {p.name for p in files}
        total=collections.Counter()
        for path in files:
            suite=ET.parse(path).getroot();assert int(suite.attrib['tests'])>0
            for key in ['tests','failures','errors','skipped']:total[key]+=int(suite.attrib[key])
        assert dict(total)==own['reports'][label] and total['tests']>=count
        assert total['failures']==total['errors']==total['skipped']==0
    for path,digest in read(directory/'identity-inputs.json').items():assert sha(ROOT/path)==digest,('verified input changed',path)
    artifact=directory/'identity/qualified-artifact.jar';assert sha(artifact)==own['artifactSha256']
    assert content(artifact)==content(JAR)
    return {'run':directory.relative_to(ROOT).as_posix(),'retainedArtifact':artifact.relative_to(ROOT).as_posix(),
            'artifactSha256':sha(artifact),'inputHashes':(directory/'identity-inputs.json').relative_to(ROOT).as_posix(),'reports':own['reports']}
def walk(node):
    for child in node.get('children',[]):yield child;yield from walk(child)
def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--db-run',required=True);parser.add_argument('--contract-run',required=True)
    parser.add_argument('--write-candidate',action='store_true')
    parser.add_argument('--state',choices=['IN_PROGRESS','DONE'],default='IN_PROGRESS');args=parser.parse_args()
    db=run(args.db_run,'db',[
        'TEST-lab.backend.identity.database.DatabaseIsolationIT.xml',
        'TEST-lab.backend.identity.credential.IdentityCredentialWorkflowIT.xml',
        'TEST-lab.backend.identity.credential.CredentialPackagedIT.xml',
        'TEST-lab.backend.identity.credential.IdentitySessionAuthorityIT.xml'],81,'database')
    contract=run(args.contract_run,'contract',['TEST-lab.backend.identity.TransportTrustIT.xml','TEST-lab.backend.identity.WireContractIT.xml','TEST-lab.backend.identity.session.SessionRuntimeIT.xml'],116,'transport')
    browser=read(ROOT/args.contract_run/'session-browser-report.json')
    assert browser['status']=='PASS' and browser['checks']>=20 and len(browser['cases'])==browser['checks'] and browser['version']
    assert 'late login Set-Cookie is applied but revoked A never regains authority' in browser['cases']
    assert sha(JAR)==contract['artifactSha256']
    smoke=read(E/'artifact-identity/artifact-smoke.json')
    assert smoke['artifactSha256']==sha(JAR) and len(smoke['cases'])==7 and all(c['status']=='PASS' for c in smoke['cases'])
    for case in smoke['cases']:
        if 'port' in case:closed(case['port'])
    tree_path=E/'identity-dependency-tree.json';tree=read(tree_path);resolved=[];seen=set();cache_jars={}
    for item in walk(tree):
        key=(item['groupId'],item['artifactId'],item['type'],item.get('classifier',''),item['version'],item.get('scope','compile'))
        if key in seen:continue
        seen.add(key);group,aid,typ,classifier,version,scope=key
        name=aid+'-'+version+('-'+classifier if classifier else '')+'.'+typ
        file=ROOT/'.tools/m2/repository'/group.replace('.','/')/aid/version/name
        if typ=='jar':cache_jars[name]=sha(file)
        resolved.append({'coordinate':f'{group}:{aid}:{typ}:{classifier}:{version}','scope':scope,'sha256':sha(file)})
    resolved.sort(key=lambda x:(x['coordinate'],x['scope']))
    assert any(a['coordinate']=='org.springframework.session:spring-session-jdbc:jar::4.1.1' for a in resolved)
    with zipfile.ZipFile(JAR) as archive:
        names=archive.namelist();libs=[n for n in names if n.startswith('BOOT-INF/lib/') and n.endswith('.jar')]
        # Boot's packager copies this nested resource from its loader-tools
        # dependency; it is NOT a separately resolved cache artifact.
        loader=ROOT/'.tools/m2/repository/org/springframework/boot/spring-boot-loader-tools/4.1.1/spring-boot-loader-tools-4.1.1.jar'
        with zipfile.ZipFile(loader) as packager:injected=hashlib.sha256(packager.read('META-INF/jarmode/spring-boot-jarmode-tools.jar')).hexdigest()
        cache_jars['spring-boot-jarmode-tools-4.1.1.jar']=injected
        for lib in libs:assert hashlib.sha256(archive.read(lib)).hexdigest()==cache_jars[Path(lib).name],('packaged dependency mismatch',lib)
        assert not any(re.search(r'flyway|junit|mockito',n,re.I) for n in libs)
        assert not any(re.search(r'\.p12$|db-test-migration|db-failure|db-recovery|IdentitySessionAuthorityIT|password-corpus',n) for n in names)
        assert archive.read('BOOT-INF/classes/db/migration/V3__session_authority.sql')==(ROOT/'backend/services/identity-service/src/main/resources/db/migration/V3__session_authority.sql').read_bytes()
    snapshot_path=E/'identity-dependency-snapshot.json'
    snapshot_text=None;manifest=None
    if args.write_candidate:
        snapshot={'schemaVersion':1,'scope':f'BL-R04a {args.state} local DB/credential atomicity and opt-in owned lab auth TLS/browser; not distributed completion/production edge/release qualification',
                  'checkedAt':datetime.now(timezone.utc).isoformat(),'sourceTreeSha256':sha(tree_path),'artifacts':resolved,
                  'packagerInjectedRuntime':[{'coordinate':'org.springframework.boot:spring-boot-jarmode-tools:jar::4.1.1',
                       'sha256':injected,'sourceCoordinate':'org.springframework.boot:spring-boot-loader-tools:jar::4.1.1',
                       'sourceCacheSha256':sha(loader),'sourceResource':'META-INF/jarmode/spring-boot-jarmode-tools.jar'}]}
        snapshot_text=json.dumps(snapshot,indent=2)+'\n'
        manifest=read(MANIFEST);manifest['services']['identity']['workingCandidate']={
            'task':'BL-R04a','state':args.state,'scope':snapshot['scope'],'artifactSha256':sha(JAR),
            'dependencySnapshot':snapshot_path.relative_to(ROOT).as_posix(),'dependencySnapshotSha256':hashlib.sha256(snapshot_text.encode('utf-8')).hexdigest(),
            'dependenciesByScope':dict(collections.Counter(a['scope'] for a in resolved)),
            'packagedRuntimeLibraries':len(libs),'dbEvidence':db,'transportRegression':contract,
            'browserEvidence':(Path(args.contract_run)/'session-browser-report.json').as_posix(),
            'bootstrapSmokeEvidence':(E/'artifact-identity/artifact-smoke.json').relative_to(ROOT).as_posix(),
            'qualifiedBaseline':'Existing R03 service artifact/dependency/evidence fields are retained historical qualified baseline, NOT this working candidate.'}
    else:snapshot=read(snapshot_path)
    assert snapshot['artifacts']==resolved and snapshot['sourceTreeSha256']==sha(tree_path)
    assert snapshot['packagerInjectedRuntime'][0]['sha256']==injected and snapshot['packagerInjectedRuntime'][0]['sourceCacheSha256']==sha(loader)
    candidate=(manifest or read(MANIFEST))['services']['identity']['workingCandidate']
    assert candidate['task']=='BL-R04a' and candidate['state']==args.state and candidate['artifactSha256']==sha(JAR)
    snapshot_digest=hashlib.sha256(snapshot_text.encode('utf-8')).hexdigest() if snapshot_text else sha(snapshot_path)
    assert candidate['dependencySnapshotSha256']==snapshot_digest and candidate['dbEvidence']==db and candidate['transportRegression']==contract
    assert candidate['browserEvidence']==(Path(args.contract_run)/'session-browser-report.json').as_posix()
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
    done={'BL-R00','BL-R01','BL-R01a','BL-R01b','BL-R01c','BL-R01d','BL-R02a','BL-R02b','BL-R03'}
    for task,(state,deps) in rows.items():
        assert state==(args.state if task=='BL-R04a' else 'DONE' if task in done else 'PLANNED'),(task,state)
        assert all(dep in rows for dep in deps)
    active,visited=set(),set()
    def dag(task):
        assert task not in active,task
        if task in visited:return
        active.add(task)
        for dep in rows[task][1]:dag(dep)
        active.remove(task);visited.add(task)
    for task in rows:dag(task)
    exceptions={'scripts/services.ps1','scripts/db_lab.py','ops/identity/README.md',
                'backend/services/identity-service/pom.xml',
                'backend/services/identity-service/src/main/java/lab/backend/identity/configuration/BootstrapListenerGuard.java',
                'backend/services/identity-service/src/main/java/lab/backend/identity/transport/TransportConfiguration.java',
                'backend/services/identity-service/src/main/java/lab/backend/identity/transport/TransportBoundaryFilter.java',
                'backend/services/identity-service/src/test/java/lab/backend/identity/TransportTrustIT.java',
                'backend/services/identity-service/src/main/java/lab/backend/identity/credential/CredentialWorkflow.java',
                'backend/services/identity-service/src/main/java/lab/backend/identity/credential/CredentialService.java',
                'backend/services/identity-service/src/test/java/lab/backend/identity/database/DatabaseIsolationIT.java',
                'backend/services/identity-service/src/test/java/lab/backend/identity/credential/IdentityCredentialWorkflowIT.java'}
    unchanged=0
    old=ROOT/'artifacts/quality/R03/a38ad5e0-b816-44dd-9e6f-cbaba564f190'
    for svc in ['identity','content','learning']:
        for path,digest in read(old/f'{svc}-inputs.json').items():
            path=path.replace('\\','/')
            if path in exceptions:continue
            assert sha(ROOT/path)==digest,('unrelated input changed',path);unchanged+=1
    for path,digest in read(ROOT/'artifacts/quality/R02b/baseline.json').items():
        if path in ['frontend/package.json','frontend/package-lock.json']:assert sha(ROOT/path)==digest;unchanged+=1
    for port in [8080,8443,18081,18082,18083]:closed(port)
    helper=read(E/'helper-tests.json');assert len(helper['cases'])>=49 and all(c['result']=='PASS' for c in helper['cases'])
    guards=(E/'lab-guards.log').read_text(encoding='utf-8-sig');assert 'Ran 8 tests' in guards and 'OK' in guards
    secrets=[]
    for path in (ROOT/'.tools/db-lab').glob('run-*/test-credentials.properties'):
        secrets += [line.split('=',1)[1] for line in path.read_text(encoding='utf-8').splitlines() if '.password=' in line]
    for path in (ROOT/'.tools/trust-lab').glob('run-*/*.properties'):
        secrets += [line.split('=',1)[1].replace(r'\=','=') for line in path.read_text(encoding='utf-8').splitlines() if line.startswith(('storePassword=','dbPassword=','throttleKey='))]
    forbidden={s.encode('ascii') for s in secrets if len(s)>=32 and len(set(s))>1}
    scan=markdown+list((ROOT/'ops').rglob('*.json'))+list((ROOT/'scripts').rglob('*.py'))+list((ROOT/'scripts').rglob('*.ps1'))
    scan+=list((ROOT/'backend/services').rglob('*.java'))+list((ROOT/'backend/services').rglob('*.sql'))
    scan+=list((ROOT/'scripts').rglob('*.mjs'))+list((ROOT/'contracts/http/identity').glob('*.json'))
    # Qualified outputs must also be free of generated credential/HMAC material.
    # Secret mounts/PKI artifacts themselves are deliberately outside this scan.
    for relative in [args.db_run,args.contract_run]:
        directory=ROOT/relative
        scan += [p for p in directory.rglob('*') if p.is_file() and p.suffix in {'.log','.xml','.json'}]
        if (directory/'pki-manifest.json').is_file():
            trust=(ROOT/read(directory/'pki-manifest.json')['directory']).resolve()
            assert trust.is_relative_to(ROOT/'.tools/trust-lab')
            scan+=list(trust.rglob('*.log'))
    for path in scan:
        if 'target' in path.parts:continue
        data=path.read_bytes()
        assert not any(secret in data for secret in forbidden),('generated secret leaked',path)
    for path in ['scripts/db_lab.py','scripts/trust_lab.py','scripts/tests/session_checkpoint_review.py']:
        ast.parse((ROOT/path).read_text(encoding='utf-8'))
    # No partial DONE/candidate record when any hash, suite, link, scope, cleanup
    # or secret gate failed above. This tool never edits backlog task states.
    if args.write_candidate:
        with snapshot_path.open('w',encoding='utf-8',newline='\n') as output:output.write(snapshot_text)
        with MANIFEST.open('w',encoding='utf-8',newline='\n') as output:output.write(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'scope':f'R04a {args.state} leaf evidence audit only; not full Identity/R1','markdown':len(markdown),'localLinks':links,
                      'taskIds':len(rows),'dagEdges':169,'unchangedBaselineInputs':unchanged,
                      'dbTests':db['reports']['database']['tests'],'transportAndAuthTlsTests':contract['reports']['transport']['tests'],'browserChecks':browser['checks'],'unitBootstrap':63,'runtimeLibraries':len(libs),
                      'dependenciesByScope':dict(collections.Counter(a['scope'] for a in resolved)),
                      'artifactSha256':sha(JAR),'status':'PASS'},indent=2))
if __name__=='__main__':main()
