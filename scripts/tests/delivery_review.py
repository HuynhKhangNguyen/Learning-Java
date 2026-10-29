"""Audit đóng R02c: chỉ đọc source/evidence, ghi kết quả vào vùng ignored.

Phải có report PG/mTLS thật, không thay bằng helper doubles. SQL probe và proof
port test-only không nghiệm thu publication/coordinator/restore production.
"""
from __future__ import annotations
import argparse, ast, collections, hashlib, json, re, socket, zipfile
from pathlib import Path
from urllib.parse import unquote
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
E = ROOT / 'artifacts/quality/R02c'
SERVICES = ('identity', 'content', 'learning')
BASE = (
    'aaOwnProducerCliAndPackagedReceiverCommitBothLocalEffects',
    'abDiscardedAckAndRestartResendExactMessageWithoutSecondEffect',
    'acPublicWrongCallerAndBrowserHeadersCannotDeliver',
    'adFutureGapAndBadDigestAreNoEffectDurablePoison',
    'afActualProducerAbortAndReceiverRecoveryPreserveBytesAndNoEffect',
    'agDispatcherProcessCrashBeforeAndAfterReceiverCommit',
    'ahBoundedNetworkFailuresNeverAcknowledgeOrLeakMessage',
    'aiWrongAckFromAuthenticatedPeerCannotMarkDelivered',
    'ajWrongReceiverCertificateCannotMarkDelivered')
AUTH = 'aeAuthLanePassesActualInt02WhileStrictPoisonRemains'
VARIANTS = {BASE[5]: 2, BASE[6]: 4, BASE[7]: 7, BASE[8]: 2}
SELECTORS = {'DATA-10': (BASE[0], BASE[5]), 'DATA-11': (BASE[1],),
             'DATA-12': (BASE[3],), 'DATA-13': (BASE[2], BASE[3], BASE[4], BASE[6], BASE[8]),
             'DATA-14': (BASE[7],), 'DATA-15': (AUTH,)}

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def read(path): return json.loads(path.read_text(encoding='utf-8-sig'))
def closed(port):
    with socket.socket() as s:
        s.settimeout(1)
        assert s.connect_ex(('127.0.0.1', port)) != 0, ('listener open', port)
def entries(path):
    with zipfile.ZipFile(path) as z:
        return {n: hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist()}
def unit_names(service):
    names = {service.title()+'BootstrapConfigTests', service.title()+'BootstrapHttpTests',
             'delivery.DeliveryWireTests', 'delivery.DeliveryHttpHandlerTests', 'delivery.DeliveryRuntimeConfigTests'}
    if service == 'identity':
        names |= {'credential.CredentialPolicyTests', 'credential.KdfBenchmarkTests',
                  'session.SessionHttpHandlerTests', 'delivery.AuthDeliveryWireTests'}
    else: names.add('identityaccess.AdmissionWireTests')
    return names
def it_names(service, task):
    if task == 'relay': return {'delivery.DeliveryRuntimeIT'}
    if task == 'contract':
        return {'TransportTrustIT', 'WireContractIT',
                'session.SessionRuntimeIT' if service == 'identity' else 'identityaccess.AdmissionRuntimeIT'}
    names = {'database.DatabaseIsolationIT', 'delivery.DeliveryStoreIT'}
    if service == 'identity':
        names |= {'credential.IdentityCredentialWorkflowIT', 'credential.CredentialPackagedIT',
                  'credential.IdentitySessionAuthorityIT'}
    else: names.add('identityaccess.AdmissionGateIT')
    return names
def input_names(service, task):
    # Khớp cả danh sách file với harness, không chỉ hash: file thêm sau run cũ
    # chưa được build thì không được âm thầm kế thừa qualification của run đó.
    paths = {ROOT/'backend/pom.xml', ROOT/'backend/services'/f'{service}-service/pom.xml',
             ROOT/'scripts/services.ps1', ROOT/'scripts/db_lab.py', ROOT/'ops/db-lab/layout.json'}
    directories = [ROOT/'backend/services'/f'{service}-service/src', ROOT/'ops'/service,
                   ROOT/'contracts/http'/('identity' if service=='identity' else 'admission')]
    if service=='identity': paths.add(ROOT/'scripts/tests/session_browser.mjs')
    if task=='relay':
        directories.append(ROOT/'contracts/http/delivery')
        for peer in SERVICES:
            paths.add(ROOT/'backend/services'/f'{peer}-service/pom.xml')
            directories += [ROOT/'backend/services'/f'{peer}-service/src', ROOT/'ops'/peer]
    if task!='db': directories.append(ROOT/'contracts/http/common')
    for directory in directories: paths |= {p for p in directory.rglob('*') if p.is_file()}
    return {p.relative_to(ROOT).as_posix() for p in paths}
def xml_reports(directory, service, expected):
    """Đối chiếu suite/case thật và tổng số; missing, zero, skip hoặc lỗi đều fail."""
    files = list(directory.glob('TEST-*.xml'))
    actual = {p.name.removeprefix('TEST-lab.backend.'+service+'.').removesuffix('.xml') for p in files}
    assert actual == expected, ('suite names', directory, actual, expected)
    total = collections.Counter(); cases = {}
    for file in files:
        suite = ET.parse(file).getroot(); name = file.name.removeprefix('TEST-lab.backend.'+service+'.').removesuffix('.xml')
        assert int(suite.attrib['tests']) > 0
        names = [c.attrib['name'] for c in suite.findall('testcase')]
        assert len(names) == int(suite.attrib['tests']) and len(set(names)) == len(names)
        assert not suite.findall('.//failure') and not suite.findall('.//error') and not suite.findall('.//skipped')
        cases[name] = names
        for key in ('tests', 'failures', 'errors', 'skipped'): total[key] += int(suite.attrib[key])
    assert total['tests'] > 0 and total['failures'] == total['errors'] == total['skipped'] == 0
    return dict(total), cases
def verify_run(relative, service, task, case=None):
    """Kiểm run đã đóng cluster, đúng lớp/case, source hiện hành và artifact retained."""
    run = (ROOT / relative).resolve(); assert run.is_relative_to(E) and run != E
    report = read(run/'report.json')
    assert report['status'] == 'PASS' and report['clusterStopped']
    assert (report['service'], report['task'], report['case']) == (service, task, case)
    assert report['runtime'] == 'postgres (PostgreSQL) 18.6' and len(report['services']) == 1
    closed(report['port']); own = report['services'][0]; assert own['status'] == 'PASS'
    unit, _ = xml_reports(run/service/'surefire-reports', service, unit_names(service))
    integration, cases = xml_reports(run/service/'failsafe-reports', service, it_names(service, task))
    layer = {'db':'database', 'contract':'transport', 'relay':'delivery'}[task]
    assert own['reports'] == {'bootstrap':unit, layer:integration}
    if task == 'relay':
        expected = SELECTORS[case] if case else BASE + ((AUTH,) if service != 'identity' else ())
        names = cases['delivery.DeliveryRuntimeIT']
        if case == 'DATA-15': assert service in ('content', 'learning')
        for name in expected:
            assert sum(c.startswith(name) for c in names) == VARIANTS.get(name, 1), ('missing/extra variant', name)
        assert all(sum(c.startswith(name) for name in expected) == 1 for c in names), ('unexpected case', case)
    if task == 'db':
        assert len(cases['delivery.DeliveryStoreIT']) == (43 if service == 'identity' else 39)
    hashes = read(run/(service+'-inputs.json')); assert len(hashes) >= 20
    assert {p.replace(chr(92),'/') for p in hashes} == input_names(service,task), ('qualified input inventory changed', service, relative)
    for path, digest in hashes.items(): assert sha(ROOT/path) == digest, ('qualified input changed', path)
    retained = run/service/'qualified-artifact.jar'
    assert sha(retained) == own['artifactSha256']
    # ZIP SHA có thể khác vì timestamp build; so mọi entry giải nén để chứng minh
    # candidate giữ đúng code/resources/libs, không giả hai ZIP byte-identical.
    current = ROOT/'backend/services'/f'{service}-service/target/{service}-service-0.1.0-SNAPSHOT.jar'
    assert entries(retained) == entries(current), ('artifact entry mismatch', service, relative)
    return {'run':run.relative_to(ROOT).as_posix(), 'artifactSha256':sha(retained),
            'reports':own['reports'], 'inputHashes':(run/(service+'-inputs.json')).relative_to(ROOT).as_posix()}
def walk(node):
    for child in node.get('children', []): yield child; yield from walk(child)
def artifact_review(service):
    """Đối chiếu dependency cache với từng thư viện trong JAR; loại fixture/secret runtime."""
    tree = E/(service+'-dependency-tree.json'); cache = {}; resolved = []; seen = set()
    dependency_tree = read(tree)
    # Hai peer có thể dùng cùng libraries; vẫn phải giữ tree của đúng service,
    # không nhận nhầm một module khác chỉ vì các hash thư viện đều trùng.
    assert (dependency_tree['groupId'],dependency_tree['artifactId'],dependency_tree['version']) == ('lab.backend',service+'-service','0.1.0-SNAPSHOT')
    for item in walk(dependency_tree):
        key = (item['groupId'], item['artifactId'], item['type'], item.get('classifier',''), item['version'], item.get('scope','compile'))
        if key in seen: continue
        seen.add(key); group, aid, typ, classifier, version, scope = key
        filename = aid+'-'+version+('-'+classifier if classifier else '')+'.'+typ
        digest = sha(ROOT/'.tools/m2/repository'/group.replace('.','/')/aid/version/filename)
        cache[filename] = digest; resolved.append({'coordinate':':'.join(key[:5]), 'scope':scope, 'sha256':digest})
    loader = ROOT/'.tools/m2/repository/org/springframework/boot/spring-boot-loader-tools/4.1.1/spring-boot-loader-tools-4.1.1.jar'
    with zipfile.ZipFile(loader) as z: cache['spring-boot-jarmode-tools-4.1.1.jar'] = hashlib.sha256(z.read('META-INF/jarmode/spring-boot-jarmode-tools.jar')).hexdigest()
    jar = ROOT/'backend/services'/f'{service}-service/target/{service}-service-0.1.0-SNAPSHOT.jar'
    with zipfile.ZipFile(jar) as z:
        names = z.namelist(); libs = [n for n in names if n.startswith('BOOT-INF/lib/') and n.endswith('.jar')]
        assert libs and all(hashlib.sha256(z.read(n)).hexdigest() == cache[Path(n).name] for n in libs)
        assert not any(re.search(r'flyway|junit|mockito', n, re.I) for n in libs)
        assert not any(re.search(r'\.p12$|db-test-migration|db-failure|db-recovery|DeliveryRecoveryFixtureMain|DeliveryFaultPeer|DeliveryRuntimeIT', n) for n in names)
        for source in (ROOT/'backend/services'/f'{service}-service/src/main/resources/db/migration').glob('*.sql'):
            assert z.read('BOOT-INF/classes/db/migration/'+source.name) == source.read_bytes()
    smoke = read(E/('artifact-'+service)/'artifact-smoke.json')
    assert smoke['artifactSha256'] == sha(jar) and len(smoke['cases']) == 7 and all(c['status']=='PASS' for c in smoke['cases'])
    for c in smoke['cases']:
        if 'port' in c: closed(c['port'])
    return {'artifactSha256':sha(jar), 'dependencyTreeSha256':sha(tree), 'dependencies':resolved,
            'runtimeLibraries':len(libs), 'smokeCases':len(smoke['cases'])}

def main():
    """Ghép bằng chứng theo AC; chỉ ghi PASS khi mọi lớp được yêu cầu đều đã kiểm."""
    # Python -O/PYTHONOPTIMIZE có thể bỏ toàn bộ assert; audit phải từ chối chế độ
    # đó thay vì ghi PASS khi các điều kiện kiểm chứng không còn được thực thi.
    if not __debug__: raise RuntimeError('Audit requires Python assertions enabled; optimized mode is not PASS')
    parser = argparse.ArgumentParser()
    for service in SERVICES:
        for task in ('db','contract','relay'): parser.add_argument('--'+task+'-'+service, required=True)
    parser.add_argument('--data-run', action='append', required=True, help='DATA-xx:service:repo-relative-run-directory')
    parser.add_argument('--state', choices=('IN_PROGRESS','DONE'), default='IN_PROGRESS')
    args = parser.parse_args(); evidence = {}
    for service in SERVICES:
        evidence[service] = {task:verify_run(getattr(args,task+'_'+service),service,task) for task in ('db','contract','relay')}
    selected = []
    for value in args.data_run:
        case, service, directory = value.split(':',2); assert case in SELECTORS and service in SERVICES
        selected.append({'case':case, 'service':service, **verify_run(directory,service,'relay',case)})
    assert {p['case'] for p in selected} == set(SELECTORS), 'Every implemented DATA selector needs actual execution'
    assert {p['service'] for p in selected if p['case']=='DATA-15'} == {'content','learning'}
    artifacts = {service:artifact_review(service) for service in SERVICES}
    manifest = read(ROOT/'ops/release/dependencies.json')
    for service in SERVICES:
        candidate = manifest['services'][service]['workingCandidate']
        assert candidate['task'] == 'BL-R02c' and candidate['state'] == args.state
        assert candidate['checkpoint'] == 'docs/platform/delivery/tasks/BL-R02c.md'
        if args.state == 'DONE':
            # Manifest DONE phải trỏ đúng candidate và các lượt đã kiểm, không
            # lấy hash R04a/b/c lịch sử làm bằng chứng cho source delivery mới.
            assert candidate['artifactSha256'] == artifacts[service]['artifactSha256']
            assert candidate['dependencyTree'] == f'artifacts/quality/R02c/{service}-dependency-tree.json'
            assert candidate['dependencyTreeSha256'] == artifacts[service]['dependencyTreeSha256']
            assert candidate['packagedRuntimeLibraries'] == artifacts[service]['runtimeLibraries']
            assert candidate['dependenciesByScope'] == dict(collections.Counter(p['scope'] for p in artifacts[service]['dependencies']))
            for task in ('db','contract','relay'):
                assert candidate[task+'Evidence'] == evidence[service][task]['run']
            assert candidate['selectedEvidence'] == {p['case']:p['run'] for p in selected if p['service']==service}
            assert candidate['bootstrapSmokeEvidence'] == f'artifacts/quality/R02c/artifact-{service}/artifact-smoke.json'
            assert candidate['closingAudit'] == 'artifacts/quality/R02c/delivery-review.json'
    helper = read(E/'helper-tests.json')
    assert not helper.get('relayOnly') and len(helper['cases']) >= 200 and all(c['result']=='PASS' for c in helper['cases'])
    # Helper chạy bản script đã copy vào fixture. So với source hiện hành để
    # report cũ không vô tình chứng minh gate mới; vẫn không gọi doubles là DB thật.
    helper_fixture = Path(helper['fixtureDirectory']).resolve()
    assert helper_fixture.is_relative_to(ROOT/'.tools/test-r01a') and helper_fixture != ROOT/'.tools/test-r01a'
    for script in ('services.ps1','setup-backend.ps1'):
        assert sha(helper_fixture/'scripts'/script) == sha(ROOT/'scripts'/script), ('helper gate source changed',script)
    focused = read(E/'helper-relay-tests.json'); assert focused['relayOnly'] and len(focused['cases']) == 90
    assert all(c['result']=='PASS' for c in focused['cases'])
    guards = (E/'lab-guards.log').read_text(encoding='utf-8-sig'); assert 'Ran 8 tests' in guards and 'OK' in guards
    markdown = list(ROOT.glob('*.md')) + list((ROOT/'docs').rglob('*.md'))
    for folder in ('backend','ops','contracts'): markdown += list((ROOT/folder).rglob('README.md'))
    markdown = [p for p in markdown if 'target' not in p.parts]; links = 0
    for path in markdown:
        body = path.read_text(encoding='utf-8'); assert '\ufffd' not in body and len(re.findall(r'^```',body,re.M))%2 == 0, path
        for dest in re.findall(r'\[[^\]]*\]\(([^)]+)\)',body):
            dest = dest.strip().strip('<>')
            if re.match(r'^(https?://|mailto:|codex:|plugin:|app:|#)', dest): continue
            dest = unquote(dest.split('#')[0].split('?')[0])
            if dest: assert (path.parent/dest).exists(), (path,dest); links += 1
    rows = {}
    for line in (ROOT/'docs/delivery-backlog.md').read_text(encoding='utf-8').splitlines():
        if not re.match(r'^\| BL-R\d{2}[a-z]? · ',line): continue
        cols = [c.strip() for c in line.split('|')]; task,state = re.fullmatch(r'(BL-R\d{2}[a-z]?) · (\w+)',cols[1]).groups()
        assert task not in rows; rows[task] = (state,re.findall(r'BL-R\d{2}[a-z]?',cols[3]))
    assert rows['BL-R02c'][0] == args.state and rows['BL-R04d'][0] == 'PLANNED'
    assert rows['BL-R02'][0] == ('DONE' if args.state=='DONE' else 'PLANNED')
    assert all(rows[t][0]=='DONE' for t in ('BL-R02a','BL-R02b','BL-R04a','BL-R04b','BL-R04c'))
    # active giữ nhánh đang duyệt: quay lại một node active là chu trình dependency.
    # visited tránh duyệt lại phần DAG (đồ thị có hướng không chu trình) đã kiểm xong.
    active,visited = set(),set()
    def dag(task):
        assert task not in active, task
        if task in visited: return
        active.add(task)
        for dep in rows[task][1]: assert dep in rows; dag(dep)
        active.remove(task); visited.add(task)
    for task in rows: dag(task)
    for service in SERVICES:
        for source in (ROOT/'backend/services'/f'{service}-service/src/main').rglob('*.java'):
            body = source.read_text(encoding='utf-8')
            assert all('lab.backend.'+peer not in body for peer in SERVICES if peer!=service), source
    for path,digest in read(ROOT/'artifacts/quality/R02b/baseline.json').items():
        if path in ('frontend/package.json','frontend/package-lock.json'): assert sha(ROOT/path)==digest
    for report_path in E.glob('*/report.json'):
        report = read(report_path)
        if 'clusterStopped' in report: assert report['clusterStopped']; closed(report['port'])
    for port in (8080,8443,18081,18082,18083): closed(port)
    secrets = []
    for path in (ROOT/'.tools/db-lab').glob('run-*/test-credentials.properties'):
        secrets += [l.split('=',1)[1] for l in path.read_text(encoding='utf-8').splitlines() if '.password=' in l]
    for path in (ROOT/'.tools/trust-lab').glob('run-*/*.properties'):
        secrets += [l.split('=',1)[1].replace(r'\=','=') for l in path.read_text(encoding='utf-8').splitlines() if l.startswith(('storePassword=','dbPassword=','throttleKey='))]
    # Chỉ dò secret generated đủ entropy từ lab; không in giá trị ra log/report.
    # Một match chỉ nêu file bị lộ để operator kiểm riêng, không lộ thêm secret.
    forbidden = {s.encode('ascii') for s in secrets if len(s)>=32 and len(set(s))>1}
    scan = markdown + list((ROOT/'scripts').rglob('*.py')) + list((ROOT/'scripts').rglob('*.ps1'))
    for folder in ('ops','backend/services'):
        scan += [p for p in (ROOT/folder).rglob('*') if p.is_file() and p.suffix in {'.java','.sql','.json'} and 'target' not in p.parts]
    for proof in [p for own in evidence.values() for p in own.values()] + selected:
        scan += [p for p in (ROOT/proof['run']).rglob('*') if p.is_file() and p.suffix in {'.log','.xml','.json'}]
    for path in set(scan):
        data = path.read_bytes()
        assert not any(secret in data for secret in forbidden), ('generated secret leaked',path)
    for path in ('scripts/db_lab.py','scripts/trust_lab.py','scripts/tests/delivery_review.py'): ast.parse((ROOT/path).read_text(encoding='utf-8'))
    result = {'scope':'R02c own delivery foundation/FX04; SQL probe/test-only producer proof, not canonical coordinator/publication/lifecycle/production restore',
              'state':args.state, 'status':'PASS', 'evidence':evidence, 'selectors':selected,
              'artifacts':artifacts, 'helperCases':len(helper['cases']), 'focusedHelperCases':90,
              'markdown':len(markdown), 'localLinks':links, 'taskIds':len(rows),
              'dagEdges':sum(len(d) for _,d in rows.values())}
    (E/'delivery-review.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k not in ('evidence','selectors','artifacts')},indent=2))

if __name__ == '__main__': main()
