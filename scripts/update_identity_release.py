"""Regenerate the current Identity dependency snapshot/manifest from verified local inputs."""
from __future__ import annotations
import argparse, hashlib, json, zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TREE=ROOT/'artifacts/quality/R03/identity-dependency-tree.json'
SNAPSHOT=ROOT/'ops/release/identity-dependencies.json'
MANIFEST=ROOT/'ops/release/dependencies.json'
REPO=ROOT/'.tools/m2/repository'
JAR=ROOT/'backend/services/identity-service/target/identity-service-0.1.0-SNAPSHOT.jar'
SMOKE=ROOT/'artifacts/quality/R03/artifact-identity/artifact-smoke.json'
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def walk(node):
    for child in node.get('children',[]):
        yield child
        yield from walk(child)
def content_sha(path):
    """Compare exact uncompressed entries, not rebuild-dependent ZIP timestamps."""
    result=hashlib.sha256()
    with zipfile.ZipFile(path) as archive:
        for name in sorted(archive.namelist()):
            encoded=name.encode('utf-8');result.update(len(encoded).to_bytes(4,'big'));result.update(encoded)
            result.update(hashlib.sha256(archive.read(name)).digest())
    return result.hexdigest()
def qualified_run(relative,task,case=None):
    directory=(ROOT/relative).resolve()
    if not directory.is_relative_to(ROOT/'artifacts/quality/R03'): raise SystemExit('Evidence must be an R03 workspace run')
    report=json.loads((directory/'report.json').read_text(encoding='utf-8'))
    if report['status']!='PASS' or not report['clusterStopped'] or report['task']!=task or report['case']!=case:
        raise SystemExit('Evidence is not a passing, closed, expected task/case')
    own=next(item for item in report['services'] if item['service']=='identity')
    if own['status']!='PASS': raise SystemExit('Identity did not pass')
    inputs=directory/'identity-inputs.json'
    for path,digest in json.loads(inputs.read_text(encoding='utf-8')).items():
        if sha(ROOT/path)!=digest: raise SystemExit('Verified input changed: '+path)
    artifact=directory/'identity/qualified-artifact.jar'
    if sha(artifact)!=own['artifactSha256']: raise SystemExit('Qualified artifact hash mismatch')
    return {'run':directory.relative_to(ROOT).as_posix(),'inputHashes':inputs.relative_to(ROOT).as_posix(),
            'sha256':own['artifactSha256'],'contentSha256':content_sha(artifact),
            'retainedArtifact':artifact.relative_to(ROOT).as_posix(),'reports':own['reports']}
def main():
    parser=argparse.ArgumentParser()
    for name in ['db-run','contract-run','sec12-run','sec22-run']: parser.add_argument('--'+name,required=True)
    args=parser.parse_args()
    db=qualified_run(args.db_run,'db');contract=qualified_run(args.contract_run,'contract')
    sec12=qualified_run(args.sec12_run,'fault','SEC-12');sec22=qualified_run(args.sec22_run,'fault','SEC-22')
    if sha(JAR)!=contract['sha256'] or any(proof['contentSha256']!=content_sha(JAR) for proof in [db,contract,sec12,sec22]):
        raise SystemExit('Current artifact does not match the contract candidate and qualified credential entry contents')
    smoke=json.loads(SMOKE.read_text(encoding='utf-8'))
    if smoke['artifactSha256']!=sha(JAR) or len(smoke['cases'])!=7 or any(case['status']!='PASS' for case in smoke['cases']):
        raise SystemExit('Missing exact-candidate packaged bootstrap smoke')
    tree=json.loads(TREE.read_text(encoding='utf-8'))
    artifacts=[]; seen=set()
    for item in walk(tree):
        key=(item['groupId'],item['artifactId'],item['type'],item.get('classifier',''),item['version'],item.get('scope','compile'))
        if key in seen: continue
        seen.add(key); group,artifact,typ,classifier,version,scope=key
        suffix=('-'+classifier if classifier else '')+'.'+typ
        file=REPO/Path(*group.split('.'))/artifact/version/(artifact+'-'+version+suffix)
        if not file.is_file(): raise SystemExit(f'Missing resolved artifact: {file}')
        rel='/'.join(group.split('.'))+f'/{artifact}/{version}/{file.name}'
        artifacts.append({'coordinate':f'{group}:{artifact}:{typ}:{classifier}:{version}','scope':scope or 'compile','sha256':sha(file),'source':'https://repo.maven.apache.org/maven2/'+rel})
    artifacts.sort(key=lambda x:(x['coordinate'],x['scope']))
    snapshot={'schemaVersion':1,'scope':'BL-R03 Identity credential/KDF/maintenance + R02b transport + R02a DB test dependencies; not release SBOM, immutable lockfile or full SCA','checkedAt':datetime.now(timezone.utc).isoformat(),'generator':{'plugin':'org.apache.maven.plugins:maven-dependency-plugin:3.10.0:tree','sourceTreeSha256':sha(TREE)},'tree':tree,'artifacts':artifacts}
    SNAPSHOT.write_text(json.dumps(snapshot,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    scopes={};
    for a in artifacts: scopes[a['scope']]=scopes.get(a['scope'],0)+1
    with zipfile.ZipFile(JAR) as archive: runtime=sum(1 for n in archive.namelist() if n.startswith('BOOT-INF/lib/') and n.endswith('.jar'))
    manifest=json.loads(MANIFEST.read_text(encoding='utf-8')); identity=manifest['services']['identity']
    identity.update({'dependencySnapshotSha256':sha(SNAPSHOT),'artifactSha256':sha(JAR),'dependenciesByScope':scopes,'packagedRuntimeLibraries':runtime,'testScope':'R03 credential workflow/KDF/corpus/throttle actual PostgreSQL + R02b packaged TLS/common-wire regression; no browser session/admission/edge qualification','bootstrapSmokeEvidence':'artifacts/quality/R03/artifact-identity/artifact-smoke.json'})
    identity['transportEvidence']={**contract,'contractRun':contract['run']}
    identity['credentialEvidence']={'db':db,'sec12':sec12,'sec22':sec22,'corpusManifest':'ops/identity/password-corpus.json','dependencyTree':'artifacts/quality/R03/identity-dependency-tree.json','artifactComparison':'Each run retains its exact ZIP SHA; every uncompressed entry matches the current contract candidate. No claim that timestamp-different ZIP bytes are identical.'}
    identity['runtimeClock']='own app DB clock_timestamp plus credential workflow; JDBC runtime, no application migration'
    manifest['updatedAt']=datetime.now(timezone.utc).isoformat()
    manifest['scope']='BL-R01 build/bootstrap, R02a DB harness, R02b lab TLS/common-wire and R03 local Identity credentials; not release qualification'
    manifest['pendingEvidence'][-1]['owner']='BL-R04/BL-R05/BL-R06/BL-R31a/BL-R31c'
    manifest['pendingEvidence'][-1]['required']='Update snapshots for future business dependencies; session/admission/fences/catalog/publication, actual edge/secret isolation/rotation. R03 qualifies local credential/CLI only; R02b qualifies lab TLS/common-wire receiver foundation.'
    MANIFEST.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__': main()
