"""CMD-03: own a fresh workspace PostgreSQL cluster, run real service ITs, stop it."""
from __future__ import annotations

import argparse
from contextlib import nullcontext
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
LAB_ROOT = ROOT / ".tools/db-lab"
FLAG = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0


def run(command, *, env=None, input_text=None, log=None, timeout=60):
    # pg_ctl's Windows server child can inherit the output handle. A pipe would
    # wait for EOF until that server stops, even after pg_ctl itself has exited.
    output_context = log.open("w", encoding="utf-8") if log else nullcontext(subprocess.PIPE)
    with output_context as output:
        process = subprocess.Popen(command, cwd=ROOT, env=env, text=True,
                                   encoding="utf-8", errors="replace", stdout=output,
                                   stdin=subprocess.PIPE if input_text is not None else subprocess.DEVNULL,
                                   stderr=subprocess.STDOUT, creationflags=FLAG)
        try:
            captured, _ = process.communicate(input_text, timeout=timeout)
        except subprocess.TimeoutExpired:
            # Stop only the process tree this invocation created. Killing just
            # pwsh would leave its Maven/JVM children running after lab teardown.
            if os.name == "nt":
                cleanup = subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                                         capture_output=True, creationflags=FLAG, timeout=15, check=False)
                if cleanup.returncode:
                    if process.poll() is None:
                        process.kill()
                    process.communicate(timeout=15)
                    raise RuntimeError(f"{Path(command[0]).name} timed out; process-tree cleanup failed "
                                       f"(taskkill exit {cleanup.returncode}); descendants unconfirmed; log: {log}")
            else:
                process.kill()
            process.communicate(timeout=15)
            raise RuntimeError(f"{Path(command[0]).name} timed out; owned process tree stopped; log: {log}")
    if process.returncode:
        # Provision SQL/passwords are never echoed here. Operator can inspect ignored log.
        raise RuntimeError(f"{Path(command[0]).name} failed (exit {process.returncode}); log: {log}")
    return captured or ""


def retain_reports(service, evidence, integration_layer="database"):
    """Giữ XML thật trước build sau; peer unit-only không giả có DB/IT suite."""
    target = ROOT / "backend/services" / (service + "-service") / "target"
    result = {}
    layers = [("bootstrap", "surefire-reports")]
    if integration_layer is not None:
        layers.append((integration_layer, "failsafe-reports"))
    for layer, folder in layers:
        reports = sorted((target / folder).glob("TEST-*.xml"))
        if not reports:
            raise RuntimeError(f"Missing actual {service} {layer} reports")
        totals = {key: 0 for key in ["tests", "failures", "errors", "skipped"]}
        destination = evidence / service / folder
        destination.mkdir(parents=True, exist_ok=True)
        for report in reports:
            suite = ET.parse(report).getroot()
            for key in totals:
                totals[key] += int(suite.attrib[key])
            shutil.copyfile(report, destination / report.name)
        if totals["tests"] <= 0 or any(totals[key] for key in ["failures", "errors", "skipped"]):
            raise RuntimeError(f"Required {service} {layer} reports not passing")
        result[layer] = totals
    return result


def assert_owned(directory, run_id):
    if LAB_ROOT.resolve() != LAB_ROOT:
        raise RuntimeError("Lab root resolves through a link; refusing operation")
    directory = directory.resolve()
    if not directory.is_relative_to(LAB_ROOT.resolve()) or directory == LAB_ROOT.resolve():
        raise RuntimeError("Refusing operation outside the owned disposable lab directory")
    if (directory / "data").resolve() != directory / "data":
        raise RuntimeError("Lab data directory resolves through a link; refusing stop/cleanup")
    marker = json.loads((directory / "ownership.json").read_text(encoding="utf-8"))
    if marker != {"schemaVersion": 1, "runId": run_id, "workspace": str(ROOT), "dataDirectory": str(directory / "data")}:
        raise RuntimeError("Lab ownership marker mismatch; refusing stop/cleanup")


def write_properties(path, values):
    def escape(value):
        return str(value).replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r")
    path.write_text("\n".join(key + "=" + escape(value) for key, value in values.items()) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--service", choices=["identity", "content", "learning", "all"], required=True)
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--task", choices=["db","contract","fault","relay"], default="db")
    parser.add_argument("--case")
    parser.add_argument("--evidence-task", choices=["R02a","R02b","R02c","R03","R04a","R04b","R04c","R04d","R06a","R06b","R06c"])
    parser.add_argument("--fail-before-build", action="store_true",
                        help="R02a cleanup fixture: fail after provisioning, without running Maven")
    args = parser.parse_args()
    # Nhãn evidence không biến fixture cũ thành canonical distributed proof.
    # Chỉ named Learning driver mới chạy ba canonical apps; CMD05 selector
    # vẫn dùng cùng own-PG/PKI/input/artifact cleanup, không fixture legacy.
    canonical_selectors = {"SEC-01", "SEC-03", "SEC-05", "SEC-06", "SEC-07", "SEC-08", "SEC-11", "SEC-12", "SEC-19", "SEC-20", "SEC-23", "DATA-15"}
    canonical_runtime = args.evidence_task == "R04d" and args.service == "learning" and (
        args.task == "contract" or (args.task == "fault" and args.case in canonical_selectors))
    canonical_browser = canonical_runtime and (args.task == "contract" or args.case in {"SEC-06", "SEC-11", "SEC-19", "SEC-23"})
    # SEC10/12 Identity cố ý chạy full own-core DB bundle, không profile trust
    # hoặc legacy credential-only thiếu canonical SessionAuthority cases.
    canonical_core_fault = args.evidence_task == "R04d" and args.task == "fault" and args.service == "identity" and args.case in {"SEC-10", "SEC-12"}
    if args.evidence_task == "R06a" and (args.service != "content" or args.task not in {"db", "contract"}):
        parser.error("R06a supports only Content CMD-03 db or CMD-04 contract")
    if args.evidence_task == "R06a" and args.case:
        parser.error("R06a requires the full named CMD-03/CMD-04 baseline; --case is not supported")
    if args.evidence_task == "R06a" and args.fail_before_build:
        parser.error("--fail-before-build is an R02a cleanup fixture, not R06a evidence")
    if args.evidence_task == "R06b" and (args.service != "learning" or args.task not in {"db","contract","fault"}):
        parser.error("R06b supports only Learning CMD-03/04/05")
    if args.evidence_task == "R06b" and ((args.task in {"db","contract"} and args.case) or (args.task == "fault" and args.case not in {"DATA-01","DATA-02","SEC-16"}) or args.fail_before_build):
        parser.error("R06b baseline takes no case; fault requires DATA-01, DATA-02 or SEC-16")
    coordinator_faults = {"DATA-02","DATA-03","DATA-04","DATA-05","DATA-06","DATA-07","DATA-14"}
    if args.evidence_task == "R06c" and (args.service != "content" or args.task not in {"db", "contract", "fault"}):
        parser.error("R06c supports Content CMD-03/04/05 only")
    if args.evidence_task == "R06c" and ((args.task in {"db", "contract"} and args.case) or (args.task == "fault" and args.case not in coordinator_faults) or args.fail_before_build):
        parser.error("R06c baseline takes no case; CMD-05 requires DATA-02, DATA-03, DATA-04, DATA-05, DATA-06, DATA-07 or DATA-14")
    if args.evidence_task == "R04d" and not ((args.task == "db" and args.service == "identity") or canonical_runtime or canonical_core_fault):
        parser.error("R04d requires db Identity or contract Learning canonical driver; other layers pending")
    if canonical_runtime and args.task == "contract" and args.case:
        parser.error("R04d contract requires the full baseline; use fault for implemented canonical selectors")
    if args.case and args.case not in canonical_selectors | {"SEC-02","SEC-04","SEC-09","SEC-10","SEC-12","SEC-14","SEC-15","SEC-16","SEC-22","WIRE-01","WIRE-02","WIRE-03","WIRE-04","WIRE-05","WIRE-06","DATA-01","DATA-02","DATA-03","DATA-04","DATA-05","DATA-06","DATA-07","DATA-10","DATA-11","DATA-12","DATA-13","DATA-14","DATA-15"}:
        parser.error("Unknown receiver/common-wire case")
    if args.evidence_task not in {"R06b","R06c"} and not canonical_runtime and not canonical_core_fault and args.task == "fault" and args.case not in {"SEC-01","SEC-02","SEC-03","SEC-04","SEC-05","SEC-09","SEC-12","SEC-14","SEC-15","SEC-20","SEC-22"}:
        parser.error("Fault requires an implemented SEC case")
    if not canonical_runtime and args.case in {"SEC-01","SEC-03","SEC-05"} and (args.task != "fault" or args.service != "learning"):
        parser.error("Mutation admission selectors require fault and Learning")
    if args.case in {"SEC-02","SEC-04","SEC-09"} and (args.task != "fault" or args.service not in {"content","learning"}):
        parser.error("Admission receiver selectors require fault and Content or Learning")
    receiver_db_fault = not canonical_runtime and args.task == "fault" and args.case in {"SEC-03","SEC-04"}
    if not canonical_runtime and args.case in {"SEC-12","SEC-22"} and args.service != "identity":
        parser.error("SEC-12/22 are Identity credential cases")
    credential_fault = not canonical_runtime and not canonical_core_fault and args.task == "fault" and args.case in {"SEC-12","SEC-22"}
    if args.task == "db" and args.case:
        parser.error("DB task has no receiver case selector")
    data_cases = {"DATA-10","DATA-11","DATA-12","DATA-13","DATA-14","DATA-15"}
    if args.task == "relay" and args.case and args.case not in data_cases:
        parser.error("FX04 relay has no implemented case selector for that case")
    if args.evidence_task not in {"R06b","R06c"} and not canonical_runtime and args.case in data_cases and args.task != "relay":
        parser.error("DATA foundation selectors require relay")
    if args.task == "relay" and args.case == "DATA-15" and args.service not in {"content","learning"}:
        parser.error("DATA15 relay selector requires Content or Learning receiver; coordinator is R04d")
    if os.name != "nt":
        parser.error("R02a portable runtime is Windows x64; another platform requires separate qualification")
    manifest = json.loads((ROOT / "ops/release/postgresql.json").read_text(encoding="utf-8"))
    pg = ROOT / manifest["installDirectory"]
    for filename, expected_hash in manifest["qualifiedFiles"].items():
        if hashlib.sha256((pg / filename).read_bytes()).hexdigest() != expected_hash:
            raise RuntimeError("PostgreSQL qualified runtime hash changed; repeat setup/qualification")
    bin_dir = pg / "bin"
    versions = run([str(bin_dir / "postgres.exe"), "--version"]).strip()
    if versions != "postgres (PostgreSQL) 18.6":
        raise RuntimeError("CMD-03 requires the pinned PostgreSQL18.6 runtime")
    pwsh = shutil.which("pwsh")
    if not pwsh:
        raise RuntimeError("PowerShell7.4+ required for the service build helper")
    layout = json.loads((ROOT / "ops/db-lab/layout.json").read_text(encoding="utf-8"))["services"]
    services = list(layout) if args.service == "all" else [args.service]
    if LAB_ROOT.resolve() != LAB_ROOT:
        raise RuntimeError("Lab root resolves through a link; refusing provisioning")
    LAB_ROOT.mkdir(parents=True, exist_ok=True)
    directory = Path(tempfile.mkdtemp(prefix="run-", dir=LAB_ROOT)).resolve()
    run_id = str(uuid.uuid4())
    (directory / "ownership.json").write_text(json.dumps({"schemaVersion": 1, "runId": run_id, "workspace": str(ROOT), "dataDirectory": str(directory / "data")}, indent=2), encoding="utf-8")
    evidence_task = args.evidence_task or ("R04a" if args.task == "db" and args.service == "identity" else "R03" if credential_fault else "R02a" if args.task == "db" else "R02b")
    evidence = ROOT / "artifacts/quality" / evidence_task / run_id
    evidence.mkdir(parents=True, exist_ok=True)
    environment = {key: value for key, value in os.environ.items()
                   if not key.upper().startswith(("PG", "LAB_"))}
    environment["PGCONNECT_TIMEOUT"] = "5"
    admin_password = secrets.token_hex(24)
    password_file = directory / "init-password.txt"
    password_file.write_text(admin_password + "\n", encoding="ascii")
    data = directory / "data"
    started = False
    status = "FAIL"
    outcomes = []
    port = None
    try:
        run([str(bin_dir / "initdb.exe"), "-D", str(data), "-U", "lab_admin", "--encoding=UTF8", "--locale=C",
             "--auth-local=scram-sha-256", "--auth-host=scram-sha-256", "--pwfile=" + str(password_file)],
            # Windows bootstrap/locale scan có thể vượt 120 giây trên host bị
            # contention; đây chỉ là budget tạo disposable cluster, không nới
            # transaction/statement/network timeout dùng làm bằng chứng.
            env=environment, log=evidence / "initdb.log", timeout=240)
        password_file.unlink()
        with socket.socket() as reservation:
            reservation.bind(("127.0.0.1", 0))
            port = reservation.getsockname()[1]
        with (data / "postgresql.conf").open("a", encoding="utf-8") as config:
            config.write(f"\nlisten_addresses='127.0.0.1'\nport={port}\npassword_encryption='scram-sha-256'\n"
                         "log_statement='none'\nlog_min_error_statement='panic'\n"
                         f"backend_lab.run_id='{run_id}'\n")
        # Exact loopback host rules, no trust authentication or other host/IPv6 listener.
        (data / "pg_hba.conf").write_text("host all all 127.0.0.1/32 scram-sha-256\nhost all all 0.0.0.0/0 reject\nhost all all ::0/0 reject\n", encoding="ascii")
        assert_owned(directory, run_id)
        # Mark before starting so a partial/timeout launch is stopped in finally too.
        started = True
        run([str(bin_dir / "pg_ctl.exe"), "-D", str(data), "-l", str(evidence / "postgres.log"), "-w", "-t", "30", "start"],
            env=environment, log=evidence / "start.log", timeout=45)
        environment["PGPASSWORD"] = admin_password
        sql = ["REVOKE ALL ON DATABASE postgres, template1 FROM PUBLIC;"]
        credentials = {"lab.host": "127.0.0.1", "lab.port": port, "lab.runId": run_id, "lab.directory": directory}
        for name, spec in layout.items():
            # All identifiers come from the checked-in allowlist, never arbitrary shell/SQL input.
            if name not in {"identity", "content", "learning"} or spec != {
                "database": name + "_db", "schema": name, "owner": name + "_owner",
                "migrator": name + "_migrator", "app": name + "_app"}:
                raise RuntimeError("Unexpected DB layout; refusing SQL provisioning")
            sql.append(f"CREATE ROLE {spec['owner']} NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;")
            for kind in ["migrator", "app"]:
                password = secrets.token_hex(24)
                role = spec[kind]
                credentials[name + "." + kind + ".user"] = role
                credentials[name + "." + kind + ".password"] = password
                sql.append(f"CREATE ROLE {role} LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '{password}';")
            sql += [f"CREATE DATABASE {spec['database']} OWNER {spec['owner']} TEMPLATE template0 ENCODING 'UTF8';",
                    f"REVOKE ALL ON DATABASE {spec['database']} FROM PUBLIC;",
                    f"GRANT CONNECT ON DATABASE {spec['database']} TO {spec['migrator']}, {spec['app']};",
                    f"\\connect {spec['database']}",
                    "REVOKE ALL ON SCHEMA public FROM PUBLIC;",
                    f"CREATE SCHEMA {name} AUTHORIZATION {spec['migrator']};",
                    f"CREATE SCHEMA migration_metadata AUTHORIZATION {spec['migrator']};",
                    f"REVOKE ALL ON SCHEMA {name}, migration_metadata FROM PUBLIC;",
                    f"GRANT USAGE ON SCHEMA {name} TO {spec['app']};",
                    f"ALTER DEFAULT PRIVILEGES FOR ROLE {spec['migrator']} IN SCHEMA {name} GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO {spec['app']};",
                    f"ALTER ROLE {spec['app']} IN DATABASE {spec['database']} SET search_path={name},pg_catalog;",
                    f"ALTER ROLE {spec['migrator']} IN DATABASE {spec['database']} SET search_path={name},pg_catalog;",
                    "\\connect postgres"]
        # psql -X / ON_ERROR_STOP and SQL through stdin: no credentials in argv or output.
        run([str(bin_dir / "psql.exe"), "-X", "--no-password", "-v", "ON_ERROR_STOP=1", "-h", "127.0.0.1",
             "-p", str(port), "-U", "lab_admin", "-d", "postgres"], env=environment,
            input_text="\n".join(sql) + "\n", log=evidence / "provision.log")
        environment.pop("PGPASSWORD")
        properties = directory / "test-credentials.properties"
        write_properties(properties, credentials)
        trust_configs = None
        if args.task != "db" and not canonical_core_fault and not credential_fault and not receiver_db_fault:
            import trust_lab
            trust_configs = trust_lab.create_bundle(evidence, credentials)
        ipc = ROOT / ".tools/jvm-ipc-sockets"
        ipc.mkdir(parents=True, exist_ok=True)
        environment["JDK_JAVA_OPTIONS"] = '"-Djdk.net.unixdomain.tmpdir=' + str(ipc) + '"'
        # Explicit lab-only cap for memory-constrained hosts, inherited by packaged JVMs too.
        heap = os.environ.get("BACKEND_LAB_JVM_MAX_HEAP_MB")
        if heap:
            if heap not in {"192", "256", "384", "512"}:
                raise RuntimeError("Unsupported lab JVM heap cap")
            environment["JDK_JAVA_OPTIONS"] += f" -Xmx{heap}m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
        command_id = "CMD-03" if args.task == "db" else "CMD-04" if args.task == "contract" else "CMD-05"
        print(f"{command_id} PostgreSQL18.6 owned cluster ready; selected={args.service}; runId={run_id}", flush=True)
        if args.fail_before_build:
            raise RuntimeError("Intentional R02a harness failure; finally must close the owned cluster")
        for name in services:
            inputs = [ROOT / "backend/pom.xml", ROOT / "backend/services" / (name + "-service") / "pom.xml"]
            inputs += [p for p in (ROOT / "backend/services" / (name + "-service") / "src").rglob("*") if p.is_file()]
            inputs += [ROOT / "scripts/services.ps1", ROOT / "scripts/db_lab.py", ROOT / "ops/db-lab/layout.json"]
            if args.task == "relay" or canonical_runtime or (args.evidence_task == "R06b" and args.task in {"contract","fault"}) or (args.evidence_task == "R06c" and args.task in {"contract","fault"}):
                for relay_peer in ["identity","content","learning"]:
                    peer_root = ROOT / "backend/services" / (relay_peer + "-service")
                    inputs += [peer_root / "pom.xml"]
                    inputs += [p for p in (peer_root / "src").rglob("*") if p.is_file()]
                    inputs += [p for p in (ROOT / "ops" / relay_peer).rglob("*") if p.is_file()]
                inputs += [p for p in (ROOT / "contracts/http/delivery").rglob("*") if p.is_file()]
            if canonical_runtime:
                # Snapshot mọi service/contract mà driver dùng trước build; source
                # peer đổi giữa run phải fail, không chỉ kiểm source Learning.
                inputs += [ROOT / "scripts/trust_lab.py", ROOT / "scripts/tests/session_browser.mjs",
                           ROOT / "scripts/tests/canonical_session_browser.mjs", ROOT / "frontend/package.json",
                           ROOT / "frontend/package-lock.json"]
                # Toolchain/wrapper và auditor cũng là input qualification;
                # không cho đổi pin/gate giữa actual run rồi nhận current PASS.
                inputs += [ROOT / path for path in (
                    "backend/mvnw", "backend/mvnw.cmd", "backend/.mvn/wrapper/maven-wrapper.properties",
                    "ops/release/dependencies.json", "ops/release/postgresql.json",
                    "scripts/tests/backend-build.Tests.ps1", "scripts/tests/r04d_admission_review.py",
                    "scripts/tests/r04d_runtime_review.py", "scripts/tests/test_r04d_runtime_review.py",
                )]
                for contract in ["identity", "admission"]:
                    inputs += [p for p in (ROOT / "contracts/http" / contract).rglob("*") if p.is_file()]
            if name == "identity":
                inputs += [p for p in (ROOT / "ops/identity").rglob("*") if p.is_file()]
                inputs += [p for p in (ROOT / "contracts/http/identity").rglob("*") if p.is_file()]
                inputs += [ROOT / "scripts/tests/session_browser.mjs"]
            if name == "content":
                inputs += [p for p in (ROOT / "ops/content").rglob("*") if p.is_file()]
                inputs += [p for p in (ROOT / "contracts/http/admission").rglob("*") if p.is_file()]
                if args.evidence_task in {"R06a", "R06c"}:
                    inputs += [p for p in (ROOT / "contracts/http/content/internal/publication").rglob("*") if p.is_file()]
                    inputs += [p for p in (ROOT / "contracts/fixtures/publication").rglob("*") if p.is_file()]
                    # Gate/auditor/toolchain inputs phải nằm trong snapshot; thay
                    # named case hoặc AJV lock sẽ làm evidence cũ stale.
                    inputs += [ROOT / path for path in (
                        "scripts/tests/backend-build.Tests.ps1", "scripts/publication/schema-parity.mjs",
                        "scripts/trust_lab.py", "ops/release/dependencies.json",
                        "ops/release/postgresql.json", "frontend/package.json", "frontend/package-lock.json",
                    )]
                if args.evidence_task == "R06c":
                    inputs += [p for p in (ROOT / "contracts/http/learning/internal/publication").rglob("*") if p.is_file()]
            if name == "learning":
                inputs += [p for p in (ROOT / "ops/learning").rglob("*") if p.is_file()]
                inputs += [p for p in (ROOT / "contracts/http/admission").rglob("*") if p.is_file()]
                if args.evidence_task == "R06b":
                    inputs += [p for p in (ROOT / "contracts/http/learning/internal/publication").rglob("*") if p.is_file()]
                    inputs += [p for p in (ROOT / "contracts/http/content/internal/publication").rglob("*") if p.is_file()]
                    inputs += [p for p in (ROOT / "contracts/fixtures/publication").rglob("*") if p.is_file()]
                    inputs += [ROOT / "scripts/trust_lab.py", ROOT / "ops/release/dependencies.json", ROOT / "ops/release/postgresql.json"]
            if args.task != "db":
                inputs += [p for p in (ROOT / "contracts/http/common").rglob("*") if p.is_file()]
            input_hashes = {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs}
            (evidence / (name + "-inputs.json")).write_text(json.dumps(input_hashes,indent=2),encoding="utf-8")
            command = [pwsh, "-NoProfile", "-File", str(ROOT / "scripts/services.ps1"), "-Task", args.task,
                       "-Service", name, "-LabConfig", str(properties)]
            if trust_configs:
                command.extend(["-TrustConfig",str(trust_configs[name])])
            if args.case:
                command.extend(["-Case",args.case])
            if args.evidence_task:
                command.extend(["-EvidenceTask",args.evidence_task])
            if args.offline:
                command.append("-Offline")
            # R04d Identity có hai pha Maven: canonical/credentials rồi delivery.
            # Fresh-flow run đo pha đầu 463s, nên tổng 600s cắt suite cuối chưa xong.
            # Budget tổng riêng vẫn bounded; không đổi timeout HTTP/DB transaction.
            # Full21 đã bị tổng900s cắt khi còn actual maintenance/restarts,
            # trước completed XML. Legacy2 đã có strict PASS122.098s (failed
            # observation attempt253.98s), ngoài fresh three-app build/browser.
            # Full contract cho headroom bounded1200s để thu đủ XML; selected
            # layers/core giữ900s. Không nới per-test/HTTP/KDF/SQL/retry budget.
            verification_budget = 1200 if canonical_runtime and args.task == "contract" else 900 if canonical_runtime or canonical_core_fault or (args.evidence_task == "R04d" and args.task == "db" and name == "identity") or (args.evidence_task == "R06b" and args.task in {"contract","fault"}) or (args.evidence_task == "R06c" and args.task in {"contract","fault"}) else 600
            run(command, env=environment, log=evidence / (name + "-verify.log"), timeout=verification_budget)
            if any(hashlib.sha256((ROOT / p).read_bytes()).hexdigest()!=digest for p,digest in input_hashes.items()):
                raise RuntimeError("Build inputs changed during verification; repeat the affected service, no PASS")
            artifact = ROOT / "backend/services" / (name + "-service") / "target" / (name + "-service-0.1.0-SNAPSHOT.jar")
            artifact_hash = hashlib.sha256(artifact.read_bytes()).hexdigest()
            qualified_artifact = evidence / name / "qualified-artifact.jar"
            qualified_artifact.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(artifact,qualified_artifact)
            outcomes.append({"service": name, "status": "PASS", "log": name + "-verify.log",
                             "artifactSha256": artifact_hash,
                             "reports": retain_reports(name, evidence, "canonical-runtime" if canonical_runtime else "delivery" if args.task == "relay" else "database" if args.task == "db" or receiver_db_fault or canonical_core_fault else "credential" if credential_fault else "transport")})
            if args.evidence_task == "R06b" and args.task in {"contract","fault"}:
                peer_artifact = ROOT / "backend/services/content-service/target/content-service-0.1.0-SNAPSHOT.jar"
                peer_destination = evidence / "content/qualified-artifact.jar"
                peer_destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(peer_artifact, peer_destination)
                outcomes[-1]["peers"] = [{"service": "content", "artifactSha256": hashlib.sha256(peer_artifact.read_bytes()).hexdigest(),
                                           "reports": retain_reports("content", evidence, None)}]
            if args.evidence_task == "R06c" and args.task in {"contract","fault"}:
                peer_artifact = ROOT / "backend/services/learning-service/target/learning-service-0.1.0-SNAPSHOT.jar"
                peer_destination = evidence / "learning/qualified-artifact.jar"
                peer_destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(peer_artifact, peer_destination)
                outcomes[-1]["peers"] = [{"service": "learning", "artifactSha256": hashlib.sha256(peer_artifact.read_bytes()).hexdigest(),
                                           "reports": retain_reports("learning", evidence, None)}]
            if canonical_runtime:
                # Driver chạy ba JAR fresh; giữ cả hai peers và unit reports,
                # không lấy qualifiedDeliveryCandidate cũ làm current artifact.
                peers = []
                for peer in ["identity", "content"]:
                    peer_artifact = ROOT / "backend/services" / (peer + "-service") / "target" / (peer + "-service-0.1.0-SNAPSHOT.jar")
                    destination = evidence / peer / "qualified-artifact.jar"
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(peer_artifact, destination)
                    peers.append({"service": peer, "artifactSha256": hashlib.sha256(peer_artifact.read_bytes()).hexdigest(),
                                  "reports": retain_reports(peer, evidence, None)})
                outcomes[-1]["peers"] = peers
                # App logs không chứa secret theo runtime contract; giữ riêng để
                # review startup/restart. Không copy config/cookie/proof/PKI keys.
                for peer in ["identity", "content", "learning"]:
                    shutil.copyfile(trust_configs[name].parent / (peer + "-canonical.log"), evidence / (peer + "-canonical.log"))
                for purpose in (["LOGOUT_ALL", "PASSWORD_CHANGE"] if canonical_browser else []):
                    browser_report = trust_configs[name].parent / ("canonical-browser-" + purpose + ".json")
                    if not browser_report.is_file():
                        raise RuntimeError("Required canonical browser report missing; not PASS")
                    shutil.copyfile(browser_report, evidence / browser_report.name)
            if name == "identity" and args.task == "contract":
                browser_report = trust_configs[name].parent / "session-browser-report.json"
                if not browser_report.is_file():
                    raise RuntimeError("Required actual browser session report missing; not PASS")
                browser_result = json.loads(browser_report.read_text(encoding="utf-8"))
                if browser_result.get("status") != "PASS" or browser_result.get("checks", 0) < 8:
                    raise RuntimeError("Required actual browser session cases not passing")
                shutil.copyfile(browser_report, evidence / "session-browser-report.json")
                outcomes[-1]["browser"] = browser_result
            print(f"PASS: {command_id} {name}; actual reports checked by helper; scope={args.task}", flush=True)
        status = "PASS"
    except Exception:
        if canonical_runtime:
            # Giữ XML/log thất bại trước clean ở lượt sau. Không copy config
            # chứa password/key/proof; evidence FAIL không thành qualification.
            for peer in ["identity", "content", "learning"]:
                target = ROOT / "backend/services" / (peer + "-service") / "target"
                for folder in ["surefire-reports", "failsafe-reports"]:
                    for xml in (target / folder).glob("TEST-*.xml"):
                        destination = evidence / "failed-reports" / peer / folder / xml.name
                        destination.parent.mkdir(parents=True, exist_ok=True)
                        shutil.copyfile(xml, destination)
                if trust_configs:
                    app_log = trust_configs["learning"].parent / (peer + "-canonical.log")
                    if app_log.is_file():
                        shutil.copyfile(app_log, evidence / (peer + "-canonical.log"))
            if trust_configs:
                # Redacted browser phase diagnostics vẫn phải giữ khi driver lỗi.
                for browser_file in trust_configs["learning"].parent.glob("canonical-browser-*"):
                    if browser_file.suffix in {".json", ".log"}:
                        shutil.copyfile(browser_file, evidence / browser_file.name)
        raise
    finally:
        stopped = False
        if started:
            assert_owned(directory, run_id)
            # A rejected launch may never have created a postmaster PID. Preserve
            # its original failure rather than masking it with a bogus stop error.
            if (data / "postmaster.pid").is_file():
                run([str(bin_dir / "pg_ctl.exe"), "-D", str(data), "-w", "-t", "30", "-m", "fast", "stop"],
                    env=environment, log=evidence / "stop.log", timeout=45)
            with socket.socket() as probe:
                if probe.connect_ex(("127.0.0.1", port)) == 0:
                    raise RuntimeError("Owned DB listener remained reachable after stop")
            stopped = True
        report = {"scope": ("R06c Content coordinator own-PostgreSQL CMD-03 core: durable global phase sequence, lease, abort and canonical/readiness atomicity; no packaged Content-to-Learning mTLS or DATA fault qualification" if args.task == "db" else "R06c packaged Content coordinator plus Content INT-03 and Learning INT-04 CMD-04 baseline: participant-down/lease/restart and exact STAGE-FENCE-RELEASE completion; no full learner T1/T2 qualification" if args.task == "contract" else "R06c packaged coordinator selected CMD-05 layer " + str(args.case) + ": actual Content/Learning mTLS and own PostgreSQL; DATA-02/06/07 are coordinator/manifest layers, not learner T1/T2/current-overlay qualification") if evidence_task == "R06c" else ("R06b Learning V4 immutable replica/receipt and local publication phase DB evidence; packaged INT-03/INT-04 mTLS and fault gates remain separate" if args.task == "db" else "R06b packaged Content INT-03 to Learning consumer and packaged Learning INT-04 selected mTLS/fault evidence; no Content coordinator/canonical commit or learner T1/T2") if evidence_task == "R06b" else "R06a Content immutable candidate/bundle and private provider selected-layer evidence; no STAGE/FENCE/canonical activation/Learning replica qualification" if evidence_task == "R06a" else "R02c own delivery foundation/FX04 selected-layer fixture evidence; no canonical coordinator/business/production publication status provider/whole-system restore qualification" if evidence_task == "R02c" else "R04c Learning mutation admission/fence receiver and actual mTLS provider-contract fixture; no canonical Identity INT-01/API-L03/T1/T2/coordinator/production qualification" if evidence_task == "R04c" else "R04b Content own DB admission/fence receiver and actual mTLS provider-contract fixture; no canonical Identity INT-01/coordinator/learner API/production qualification" if evidence_task == "R04b" else "R04a Identity local session/preauth/CSRF and opt-in owned lab auth TLS/browser; no receiver/coordinator/global completion/production edge/release qualification" if evidence_task == "R04a" else "R03 Identity credential layers; browser sessions/CSRF/edge pending" if evidence_task == "R03" else "R02a real DB harness" if args.task == "db" else "R02b real DB/TLS receiver foundation; no canonical auth/edge/business qualification",
                  "task": args.task, "case": args.case,
                  "jvmHeapCapMiB": os.environ.get("BACKEND_LAB_JVM_MAX_HEAP_MB"),
                  "runId": run_id, "service": args.service, "status": status, "runtime": versions,
                  "labDirectory": str(directory.relative_to(ROOT)), "port": port, "clusterStopped": stopped,
                  "services": outcomes}
        if evidence_task == "R04d":
            report["scope"] = ("R04d three packaged apps/three own DB/canonical mTLS baseline or exact selected fault layer: admission/dual-ACK barrier/proof/sensitive/browser, strict-poison independence and legacy credential reconciliation; no T1/T2/publication/lifecycle journal or full final DoD qualification"
                               if canonical_runtime else "R04d Identity canonical admission/auth barrier/reauth core own-PG layer and current foundation regressions only; no canonical mTLS/reauth HTTP/INT06 wire/three-app/browser/full leaf qualification")
        (evidence / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(f"{args.task} evidence: {evidence.relative_to(ROOT)}; owned listener closed={stopped}", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("Backend lab failed: " + str(error), file=sys.stderr)
        sys.exit(1)
