"""Read-only R04c final source/evidence audit; writes only ignored audit report.

Actual PG/mTLS reports are mandatory. Controlled provider is not canonical Identity;
Java mutation hook is not draft/T1/T2/global completion or production qualification.
"""
from __future__ import annotations
import argparse, ast, collections, hashlib, json, re, socket, zipfile
from pathlib import Path
from urllib.parse import unquote
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
E = ROOT / 'artifacts/quality/R04c'
JAR = ROOT / 'backend/services/learning-service/target/learning-service-0.1.0-SNAPSHOT.jar'

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path): return json.loads(path.read_text(encoding='utf-8-sig'))
def closed(port):
    with socket.socket() as s:
        s.settimeout(1)
        assert s.connect_ex(('127.0.0.1', port)) != 0, ('listener open', port)
def entries(path):
    with zipfile.ZipFile(path) as z:
        return {n: hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist()}
def walk(node):
    for child in node.get('children', []): yield child; yield from walk(child)

def verify_run(relative, task, case, suites, minimum, layer):
    run = (ROOT / relative).resolve(); assert run.is_relative_to(E) and run != E
    report = read(run / 'report.json')
    assert report['status'] == 'PASS' and report['clusterStopped']
    assert (report['service'], report['task'], report['case']) == ('learning', task, case)
    closed(report['port']); assert len(report['services']) == 1
    own = report['services'][0]; assert own['status'] == 'PASS'
    for folder, expected, count, label in [
        ('surefire-reports', {'LearningBootstrapConfigTests','LearningBootstrapHttpTests','identityaccess.AdmissionWireTests'}, 68, 'bootstrap'),
        ('failsafe-reports', suites, minimum, layer)]:
        files = list((run / 'learning' / folder).glob('TEST-*.xml'))
        assert {p.name.removeprefix('TEST-lab.backend.learning.').removesuffix('.xml') for p in files} == expected
        total = collections.Counter()
        for file in files:
            suite = ET.parse(file).getroot(); assert int(suite.attrib['tests']) > 0
            assert len(suite.findall('testcase')) == int(suite.attrib['tests'])
            for key in ['tests','failures','errors','skipped']: total[key] += int(suite.attrib[key])
        assert dict(total) == own['reports'][label] and total['tests'] >= count
        assert total['failures'] == total['errors'] == total['skipped'] == 0
    for path, digest in read(run / 'learning-inputs.json').items():
        assert sha(ROOT / path) == digest, ('qualified input changed', path)
    retained = run / 'learning/qualified-artifact.jar'
    assert sha(retained) == own['artifactSha256'] and entries(retained) == entries(JAR)
    return {'run':run.relative_to(ROOT).as_posix(), 'artifactSha256':sha(retained),
            'retainedArtifact':retained.relative_to(ROOT).as_posix(), 'reports':own['reports'],
            'inputHashes':(run / 'learning-inputs.json').relative_to(ROOT).as_posix()}

def main():
    parser = argparse.ArgumentParser()
    for name in ['db','contract','sec01','sec02','sec03','sec04','sec05','sec09','sec15']:
        parser.add_argument('--'+name+'-run', required=True)
    parser.add_argument('--state', choices=['IN_PROGRESS','DONE'], default='IN_PROGRESS')
    args = parser.parse_args()
    db = {'database.DatabaseIsolationIT','identityaccess.AdmissionGateIT'}
    runtime = {'identityaccess.AdmissionRuntimeIT'}
    evidence = {'db':verify_run(args.db_run,'db',None,db,38,'database'),
                'contract':verify_run(args.contract_run,'contract',None,runtime|{'TransportTrustIT','WireContractIT'},98,'transport')}
    for name,case,suites,count,layer in [
        ('sec01','SEC-01',runtime,1,'transport'), ('sec02','SEC-02',runtime,1,'transport'),
        ('sec03','SEC-03',db,38,'database'), ('sec04','SEC-04',db,38,'database'),
        ('sec05','SEC-05',runtime,12,'transport'), ('sec09','SEC-09',runtime,2,'transport'),
        ('sec15','SEC-15',runtime|{'TransportTrustIT'},24,'transport')]:
        evidence[name] = verify_run(getattr(args,name+'_run'),'fault',case,suites,count,layer)
    assert sha(JAR) == evidence['contract']['artifactSha256'], 'Final smoke/candidate must use exact qualified TLS artifact'
    smoke = read(E / 'artifact-learning/artifact-smoke.json')
    assert smoke['artifactSha256'] == sha(JAR) and len(smoke['cases']) == 7 and all(c['status']=='PASS' for c in smoke['cases'])
    for c in smoke['cases']:
        if 'port' in c: closed(c['port'])
    tree = E / 'learning-dependency-tree.json'; resolved=[]; seen=set(); cache={}
    for item in walk(read(tree)):
        key=(item['groupId'],item['artifactId'],item['type'],item.get('classifier',''),item['version'],item.get('scope','compile'))
        if key in seen: continue
        seen.add(key); group,aid,typ,classifier,version,scope=key
        filename=aid+'-'+version+('-'+classifier if classifier else '')+'.'+typ
        digest=sha(ROOT / '.tools/m2/repository' / group.replace('.','/') / aid / version / filename)
        cache[filename]=digest; resolved.append({'coordinate':f'{group}:{aid}:{typ}:{classifier}:{version}','scope':scope,'sha256':digest})
    resolved.sort(key=lambda a:(a['coordinate'],a['scope']))
    assert dict(collections.Counter(a['scope'] for a in resolved)) == {'compile':40,'runtime':1,'test':31}
    loader=ROOT / '.tools/m2/repository/org/springframework/boot/spring-boot-loader-tools/4.1.1/spring-boot-loader-tools-4.1.1.jar'
    with zipfile.ZipFile(loader) as z: injected=hashlib.sha256(z.read('META-INF/jarmode/spring-boot-jarmode-tools.jar')).hexdigest()
    cache['spring-boot-jarmode-tools-4.1.1.jar']=injected
    with zipfile.ZipFile(JAR) as z:
        names=z.namelist(); libs=[n for n in names if n.startswith('BOOT-INF/lib/') and n.endswith('.jar')]
        assert len(libs)==36
        for lib in libs: assert hashlib.sha256(z.read(lib)).hexdigest()==cache[Path(lib).name],lib
        assert not any(re.search(r'flyway|junit|mockito',n,re.I) for n in libs)
        assert not any(re.search(r'\.p12$|db-test-migration|db-failure|db-recovery|AdmissionGateIT',n) for n in names)
        assert z.read('BOOT-INF/classes/db/migration/V2__admission_receiver.sql') == (ROOT/'backend/services/learning-service/src/main/resources/db/migration/V2__admission_receiver.sql').read_bytes()
    markdown=list(ROOT.glob('*.md'))+list((ROOT/'docs').rglob('*.md'))
    for folder in ['backend','ops','contracts']: markdown += list((ROOT/folder).rglob('README.md'))
    markdown=[p for p in markdown if 'target' not in p.parts]; links=0
    for path in markdown:
        body=path.read_text(encoding='utf-8'); assert '\ufffd' not in body and len(re.findall(r'^```',body,re.M))%2==0,path
        for dest in re.findall(r'\[[^\]]*\]\(([^)]+)\)',body):
            dest=dest.strip().strip('<>')
            if re.match(r'^(https?://|mailto:|codex:|plugin:|app:|#)',dest): continue
            dest=unquote(dest.split('#')[0].split('?')[0])
            if dest: assert (path.parent/dest).exists(),(path,dest); links+=1
    rows={}
    for line in (ROOT/'docs/delivery-backlog.md').read_text(encoding='utf-8').splitlines():
        if not re.match(r'^\| BL-R\d{2}[a-z]? · ',line): continue
        cols=[c.strip() for c in line.split('|')]; task,state=re.fullmatch(r'(BL-R\d{2}[a-z]?) · (\w+)',cols[1]).groups()
        assert task not in rows; rows[task]=(state,re.findall(r'BL-R\d{2}[a-z]?',cols[3]))
    assert len(rows)==73 and sum(len(d) for _,d in rows.values())==169
    done={'BL-R00','BL-R01','BL-R01a','BL-R01b','BL-R01c','BL-R01d','BL-R02a','BL-R02b','BL-R03','BL-R04a','BL-R04b'}
    for task,(state,deps) in rows.items():
        assert state==(args.state if task=='BL-R04c' else 'DONE' if task in done else 'PLANNED'),(task,state)
        assert all(dep in rows for dep in deps)
    active,visited=set(),set()
    def dag(task):
        assert task not in active,task
        if task in visited:return
        active.add(task)
        for dep in rows[task][1]:dag(dep)
        active.remove(task);visited.add(task)
    for task in rows:dag(task)
    unchanged=0; peer_counts={}
    for source,svc in [('artifacts/quality/R04a/b033ae94-e719-46b7-9de8-fca1b512a9b4','identity'),('artifacts/quality/R04b/2096f815-fd08-42c5-8fb6-1f432b6ad6ad','content')]:
        peer_counts[svc]=0
        for path,digest in read(ROOT/source/(svc+'-inputs.json')).items():
            normalized=path.replace(chr(92),'/')
            if normalized.startswith('backend/services/'+svc+'-service/') or normalized=='backend/pom.xml':
                assert sha(ROOT/path)==digest,('peer source changed',path); unchanged+=1; peer_counts[svc]+=1
        assert peer_counts[svc]>=20,('missing peer source baseline',svc,peer_counts[svc])
    for path,digest in read(ROOT/'artifacts/quality/R02b/baseline.json').items():
        if path in ['frontend/package.json','frontend/package-lock.json']:assert sha(ROOT/path)==digest; unchanged+=1
    for source in (ROOT/'backend/services/learning-service/src/main').rglob('*.java'):
        body=source.read_text(encoding='utf-8'); assert 'lab.backend.identity' not in body and 'lab.backend.content' not in body,source
    helper=read(E/'helper-tests.json'); assert len(helper['cases'])>=110 and all(c['result']=='PASS' for c in helper['cases'])
    guards=(E/'lab-guards.log').read_text(encoding='utf-8-sig'); assert 'Ran 8 tests' in guards and 'OK' in guards
    for path in E.glob('*/report.json'):
        report=read(path)
        if 'clusterStopped' in report:assert report['clusterStopped']; closed(report['port'])
    for port in [8080,8443,18081,18082,18083]:closed(port)
    secrets=[]
    for path in (ROOT/'.tools/db-lab').glob('run-*/test-credentials.properties'):
        secrets += [l.split('=',1)[1] for l in path.read_text(encoding='utf-8').splitlines() if '.password=' in l]
    for path in (ROOT/'.tools/trust-lab').glob('run-*/*.properties'):
        secrets += [l.split('=',1)[1].replace(r'\=','=') for l in path.read_text(encoding='utf-8').splitlines() if l.startswith(('storePassword=','dbPassword=','throttleKey='))]
    forbidden={s.encode('ascii') for s in secrets if len(s)>=32 and len(set(s))>1}
    scan=markdown+list((ROOT/'ops').rglob('*.json'))+list((ROOT/'scripts').rglob('*.py'))+list((ROOT/'scripts').rglob('*.ps1'))
    scan += list((ROOT/'backend/services').rglob('*.java'))+list((ROOT/'backend/services').rglob('*.sql'))
    for proof in evidence.values():
        directory=ROOT/proof['run']; scan += [p for p in directory.rglob('*') if p.is_file() and p.suffix in {'.log','.xml','.json'}]
        if (directory/'pki-manifest.json').is_file():
            trust=(ROOT/read(directory/'pki-manifest.json')['directory']).resolve(); assert trust.is_relative_to(ROOT/'.tools/trust-lab'); scan += list(trust.rglob('*.log'))
    for path in scan:
        if 'target' in path.parts: continue
        data=path.read_bytes()
        assert not any(secret in data for secret in forbidden),('generated secret leaked',path)
    for path in ['scripts/db_lab.py','scripts/trust_lab.py','scripts/tests/learning_admission_review.py']:ast.parse((ROOT/path).read_text(encoding='utf-8'))
    result={'scope':'R04c Learning receiver/local mutation hook; controlled provider, not canonical Identity/draft/T1/T2/global completion',
            'state':args.state,'status':'PASS','artifactSha256':sha(JAR),'evidence':evidence,
            'dependencyTreeSha256':sha(tree),'artifacts':resolved,'runtimeLibraries':len(libs),
            'packagerInjectedRuntime':{'sha256':injected,'sourceCacheSha256':sha(loader)},
            'markdown':len(markdown),'localLinks':links,'taskIds':len(rows),'dagEdges':169,'unchangedPeerAndFeInputs':unchanged,'unchangedPeerInputsByService':peer_counts,'helperCases':len(helper['cases'])}
    (E/'receiver-review.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k not in {'artifacts','evidence'}},indent=2))

if __name__=='__main__': main()
