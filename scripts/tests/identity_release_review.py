"""R03 static evidence checks; does not substitute for actual DB/TLS tests."""
from __future__ import annotations
import argparse, ast, collections, hashlib, json, re, socket, zipfile
from pathlib import Path
from urllib.parse import unquote
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
E=ROOT/'artifacts/quality/R03'
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path): return json.loads(path.read_text(encoding='utf-8-sig'))
def closed(port):
    with socket.socket() as s:
        assert s.connect_ex(('127.0.0.1',port))!=0, ('listener remained open',port)
def verify_run(proof,task,counts,layer,case=None):
    run=ROOT/proof['run'];report=read(run/'report.json')
    assert report['task']==task and report['case']==case and report['status']=='PASS' and report['clusterStopped']
    closed(report['port'])
    for item in report['services']:
        svc=item['service'];units={'identity':35,'content':28,'learning':42}[svc]
        for name,folder,count in [('bootstrap','surefire-reports',units),(layer,'failsafe-reports',counts[svc])]:
            total=collections.Counter();files=list((run/svc/folder).glob('TEST-*.xml'));assert files
            for path in files:
                suite=ET.parse(path).getroot();assert int(suite.attrib['tests'])>0
                for key in ['tests','failures','errors','skipped']: total[key]+=int(suite.attrib[key])
            assert dict(total)==item['reports'][name] and total['tests']==count
            assert total['failures']==total['errors']==total['skipped']==0
        assert sha(run/svc/'qualified-artifact.jar')==item['artifactSha256']
        for path,digest in read(run/f'{svc}-inputs.json').items(): assert sha(ROOT/path)==digest, ('verified input changed',path)
    return report
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--state',choices=['IN_PROGRESS','DONE'],default='IN_PROGRESS');args=parser.parse_args()
    markdown=list(ROOT.glob('*.md'))+list((ROOT/'docs').rglob('*.md'))
    for folder in ['backend','ops','contracts']: markdown+=list((ROOT/folder).rglob('README.md'))
    markdown=[p for p in markdown if 'target' not in p.parts];links=0
    for path in markdown:
        body=path.read_text(encoding='utf-8');assert '\ufffd' not in body and len(re.findall(r'^```',body,re.M))%2==0,path
        for dest in re.findall(r'\[[^\]]*\]\(([^)]+)\)',body):
            dest=dest.strip().strip('<>')
            if re.match(r'^(https?://|mailto:|codex:|plugin:|app:|#)',dest): continue
            dest=unquote(dest.split('#')[0].split('?')[0])
            if dest: assert (path.parent/dest).exists(), (path,dest);links+=1
    rows={}
    for line in (ROOT/'docs/delivery-backlog.md').read_text(encoding='utf-8').splitlines():
        if not re.match(r'^\| BL-R\d{2}[a-z]? · ',line): continue
        cols=[c.strip() for c in line.split('|')];task,state=re.fullmatch(r'(BL-R\d{2}[a-z]?) · (\w+)',cols[1]).groups()
        assert task not in rows;rows[task]=(state,re.findall(r'BL-R\d{2}[a-z]?',cols[3]))
    assert len(rows)==73 and sum(len(d) for _,d in rows.values())==169
    done={'BL-R00','BL-R01','BL-R01a','BL-R01b','BL-R01c','BL-R01d','BL-R02a','BL-R02b'}
    for task,(state,deps) in rows.items():
        assert state==(args.state if task=='BL-R03' else 'DONE' if task in done else 'PLANNED'), (task,state)
        assert all(dep in rows for dep in deps)
    active,visited=set(),set()
    def dag(task):
        assert task not in active,task
        if task in visited: return
        active.add(task)
        for dep in rows[task][1]: dag(dep)
        active.remove(task);visited.add(task)
    for task in rows: dag(task)
    unchanged=0
    for svc in ['identity','content','learning']:
        old=ROOT/'artifacts/quality/R02b/5f9a3d8f-3045-4803-9ac5-3fc6aec1d483'
        for path,digest in read(old/f'{svc}-inputs.json').items():
            path=path.replace('\\','/')
            if svc=='identity' and (path.endswith('/pom.xml') or '/src/test/java/lab/backend/identity/database/' in path or '/src/test/resources/db-' in path): continue
            assert sha(ROOT/path)==digest, ('R02b baseline changed',path);unchanged+=1
    for path in ['frontend/package.json','frontend/package-lock.json']:
        assert sha(ROOT/path)==read(ROOT/'artifacts/quality/R02b/baseline.json')[path];unchanged+=1
    manifest=read(ROOT/'ops/release/dependencies.json');identity=manifest['services']['identity'];proof=identity['credentialEvidence']
    verify_run(proof['db'],'db',{'identity':53,'content':27,'learning':27},'database')
    verify_run(proof['sec12'],'fault',{'identity':44},'credential','SEC-12')
    verify_run(proof['sec22'],'fault',{'identity':40},'credential','SEC-22')
    verify_run(identity['transportEvidence'],'contract',{'identity':75},'transport')
    for path in E.glob('*/report.json'):
        item=read(path)
        if item['port'] is not None: assert item['clusterStopped'];closed(item['port'])
    for port in [8080,8443,18081,18082,18083]: closed(port)
    helper=read(E/'helper-tests.json');assert len(helper['cases'])==43 and all(c['result']=='PASS' for c in helper['cases'])
    guard=(E/'lab-guards.log').read_text(encoding='utf-8-sig');assert 'Ran 8 tests' in guard and 'OK' in guard
    for svc in ['identity','content','learning']:
        own=manifest['services'][svc];snapshot_path=ROOT/own['dependencySnapshot'];snapshot=read(snapshot_path)
        counts={'compile':42,'runtime':1,'test':31} if svc=='identity' else {'compile':40,'runtime':1,'test':31}
        assert dict(collections.Counter(a['scope'] for a in snapshot['artifacts']))==counts
        assert sha(snapshot_path)==own['dependencySnapshotSha256']
        for artifact in snapshot['artifacts']:
            group,aid,typ,classifier,version=artifact['coordinate'].split(':')
            filename=f'{aid}-{version}'+('-'+classifier if classifier else '')+'.'+typ
            assert sha(ROOT/'.tools/m2/repository'/group.replace('.','/')/aid/version/filename)==artifact['sha256']
        base=ROOT/f'backend/services/{svc}-service';jar=base/'target'/f'{svc}-service-0.1.0-SNAPSHOT.jar'
        assert sha(jar)==own['artifactSha256']==sha(ROOT/own['transportEvidence']['retainedArtifact'])
        for source in (base/'src').rglob('*.java'):
            for peer in {'identity','content','learning'}-{svc}: assert f'lab.backend.{peer}' not in source.read_text(encoding='utf-8'),source
        with zipfile.ZipFile(jar) as archive:
            names=archive.namelist();libs=[n for n in names if n.startswith('BOOT-INF/lib/') and n.endswith('.jar')]
            assert len(libs)==own['packagedRuntimeLibraries']==(38 if svc=='identity' else 36)
            assert not any(re.search(r'flyway|junit|mockito',n,re.I) for n in libs)
            assert not any(re.search(r'\.p12$|db-test-migration|db-failure|db-recovery|CredentialPackagedIT|IdentityCredentialWorkflowIT|password-corpus',n) for n in names)
            if svc=='identity':
                assert any('bcprov-jdk18on-1.86.jar' in n for n in libs)
                assert archive.read('BOOT-INF/classes/db/migration/V2__credential_workflow.sql')==(base/'src/main/resources/db/migration/V2__credential_workflow.sql').read_bytes()
    tree=E/'identity-dependency-tree.json';snapshot=read(ROOT/identity['dependencySnapshot'])
    assert snapshot['generator']['sourceTreeSha256']==sha(tree)
    smoke=read(E/'artifact-identity/artifact-smoke.json')
    assert smoke['artifactSha256']==identity['artifactSha256'] and len(smoke['cases'])==7 and all(c['status']=='PASS' for c in smoke['cases'])
    for case in smoke['cases']:
        if 'port' in case: closed(case['port'])
    corpus=read(ROOT/'ops/identity/password-corpus.json');assert sha(ROOT/'ops/identity/password-corpus.txt')==corpus['sha256']
    secrets=[]
    for path in (ROOT/'.tools/db-lab').glob('run-*/test-credentials.properties'):
        secrets += [line.split('=',1)[1] for line in path.read_text(encoding='utf-8').splitlines() if '.password=' in line]
    for path in (ROOT/'.tools/trust-lab').glob('run-*/*.properties'):
        secrets += [line.split('=',1)[1] for line in path.read_text(encoding='utf-8').splitlines() if line.startswith(('storePassword=','dbPassword=')) and len(line.split('=',1)[1])>=32]
    secrets={s.encode('ascii') for s in secrets if len(set(s))>1}
    scan=markdown+list((ROOT/'ops').rglob('*.json'))+list((ROOT/'scripts').rglob('*.py'))+list((ROOT/'scripts').rglob('*.ps1'))+list((ROOT/'backend/services').rglob('*.java'))
    scan += [p for p in E.rglob('*') if p.is_file() and p.suffix in {'.log','.json','.xml','.md'}]
    scan += list((ROOT/'.tools/trust-lab').rglob('runtime.log'))
    for path in scan:
        if 'target' in path.parts or '__pycache__' in path.parts: continue
        body=path.read_bytes();assert not any(secret in body for secret in secrets), ('generated secret leak',path)
    for path in list((ROOT/'scripts').glob('*.py'))+list((ROOT/'scripts/tests').glob('*.py')): ast.parse(path.read_text(encoding='utf-8'))
    print(f'PASS R03={args.state}: {len(markdown)} Markdown/{links} local links;73IDs/169 DAG edges;{unchanged} unchanged baseline inputs;107 DB/credential+105 unit/bootstrap;SEC12=44/SEC22=40/transport=75;43 helper/8 guards/7 exact-JAR bootstrap smoke;74 Identity artifacts/38 runtime libs;hashes/packaging/closedports/no generated secret leaks.')
if __name__=='__main__': main()
