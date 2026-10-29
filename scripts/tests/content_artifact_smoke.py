"""R01c: exercise the packaged Content artifact, without catalog/publication/DB/auth claims."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import tempfile
import time
from urllib.error import HTTPError
from urllib.request import Request, ProxyHandler, build_opener


ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "artifacts/quality/R01c"
TIMEOUT_SECONDS = 30


def stop(process: subprocess.Popen) -> None:
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)
    assert process.poll() is not None, "Artifact process still running"


def main() -> None:
    global EVIDENCE
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence-dir", type=Path,
                        help="Separate ignored evidence directory for a later bootstrap regression")
    parser.add_argument("--java-home", type=Path)
    parser.add_argument("--ipc-temp-dir", type=Path,
                        help="Explicit JVM socket temp directory for Windows runners with a failing default AF_UNIX temp path")
    arguments = parser.parse_args()
    if arguments.evidence_dir:
        evidence_directory = arguments.evidence_dir.resolve()
        quality_root = (ROOT / "artifacts/quality").resolve()
        if not evidence_directory.is_relative_to(quality_root) or evidence_directory == quality_root:
            parser.error("Smoke evidence must stay within workspace artifacts/quality")
        EVIDENCE = evidence_directory
    manifest = json.loads((ROOT / "ops/release/dependencies.json").read_text(encoding="utf-8"))
    if os.name != "nt" and not arguments.java_home:
        parser.error("R01c default runtime is Windows x64; supply --java-home for another platform and record qualification separately.")
    java_home = arguments.java_home or ROOT / manifest["toolchain"]["jdk"]["installDirectory"]
    java = java_home / "bin" / ("java.exe" if os.name == "nt" else "java")
    jvm_arguments = []
    if arguments.ipc_temp_dir:
        ipc_directory = arguments.ipc_temp_dir.resolve()
        if not ipc_directory.is_relative_to(ROOT / ".tools"):
            parser.error("Smoke IPC directory must stay inside this repo's ignored .tools directory")
        ipc_directory.mkdir(parents=True, exist_ok=True)
        jvm_arguments = [f"-Djdk.net.unixdomain.tmpdir={ipc_directory}"]
    artifact = ROOT / "backend/services/content-service/target/content-service-0.1.0-SNAPSHOT.jar"
    assert java.is_file() and artifact.is_file(), "Run the toolchain setup and CMD-02 content verify first"
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    workspace = ROOT / ".tools/content-artifact-smoke"
    workspace.mkdir(parents=True, exist_ok=True)
    # An empty working directory proves startup does not load project config/source.
    working_directory = Path(tempfile.mkdtemp(prefix="run-", dir=workspace))
    cases = []
    creation_flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    # Do not inherit Spring profile/config overrides or JVM injection into this oracle.
    child_environment = {key: value for key, value in os.environ.items()
                         if not key.upper().startswith(("SPRING_", "SERVER_", "MANAGEMENT_", "LOGGING_"))
                         and key.upper() not in {"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"}}
    scenarios = [
        ("default-profile-denied", [], "explicit 'bootstrap' profile"),
        ("pilot-profile-denied", ["--spring.profiles.active=pilot"], "explicit 'bootstrap' profile"),
        ("bind-all-denied", ["--spring.profiles.active=bootstrap", "--server.address=0.0.0.0"], "server.address=127.0.0.1"),
        ("private-port-denied", ["--spring.profiles.active=bootstrap", "--server.port=8443"], "other than 8080/8443"),
        ("management-listener-denied", ["--spring.profiles.active=bootstrap", "--management.server.port=18083"], "one loopback HTTP servlet listener"),
    ]
    for name, extra_arguments, reason in scenarios:
        log = EVIDENCE / f"artifact-{name}.log"
        with log.open("w", encoding="utf-8") as output:
            process = subprocess.Popen([str(java), *jvm_arguments, "-jar", str(artifact), *extra_arguments],
                                       cwd=working_directory, env=child_environment,
                                       stdout=output, stderr=subprocess.STDOUT, creationflags=creation_flags)
            try:
                exit_code = process.wait(timeout=TIMEOUT_SECONDS)
            finally:
                stop(process)
        body = log.read_text(encoding="utf-8")
        assert exit_code != 0 and reason in body, f"Unexpected startup result for {name}"
        assert "Tomcat initialized" not in body, f"Rejected config reached listener initialization: {name}"
        cases.append({"case": name, "exit": exit_code, "status": "PASS", "pidStopped": process.pid})
        print(f"PASS: packaged JAR {name}", flush=True)

    log = EVIDENCE / "artifact-bootstrap.log"
    port = None
    with log.open("w", encoding="utf-8") as output:
        process = subprocess.Popen([str(java), *jvm_arguments, "-jar", str(artifact), "--spring.profiles.active=bootstrap", "--server.port=0"],
                                   cwd=working_directory, env=child_environment,
                                   stdout=output, stderr=subprocess.STDOUT, creationflags=creation_flags)
        try:
            deadline = time.monotonic() + TIMEOUT_SECONDS
            while time.monotonic() < deadline:
                body = log.read_text(encoding="utf-8")
                match = re.search(r"Tomcat started on port (\d+) \(http\)", body)
                if match:
                    port = int(match.group(1))
                    break
                if process.poll() is not None:
                    raise AssertionError(f"Bootstrap exited before HTTP startup; inspect {log}")
                time.sleep(0.1)
            assert port is not None, "Bootstrap HTTP startup timed out"
            opener = build_opener(ProxyHandler({}))

            def request(path: str, method: str = "GET"):
                try:
                    return opener.open(Request(f"http://127.0.0.1:{port}{path}", method=method), timeout=5)
                except HTTPError as error:
                    return error

            with request("/bootstrap/status") as response:
                assert response.status == 200
                assert response.headers.get_content_type() == "application/json"
                assert response.headers.get("Set-Cookie") is None
                status = json.load(response)
                assert status == {"service": "content-service", "phase": "bootstrap", "businessReady": False}
            for method, path, expected in [("POST", "/bootstrap/status", 405),
                                           ("GET", "/api/v1/catalog", 404),
                                           ("GET", "/api/v1/topics/TOP-JAVA-COLLECTIONS", 404),
                                           ("GET", "/api/v1/topics/TOP-JAVA-COLLECTIONS/units/unit-1", 404),
                                           ("GET", "/internal/v1/content-bundles/bundle-1", 404),
                                           ("POST", "/api/v1/auth/login", 404),
                                           ("GET", "/api/v1/progress", 404),
                                           ("GET", "/actuator/health", 404)]:
                with request(path, method) as response:
                    assert response.status == expected, f"{method} {path}: {response.status}"
            cases.append({"case": "bootstrap-http-from-packaged-jar", "port": port, "status": "PASS"})
            print("PASS: packaged JAR HTTP status, readiness label, missing routes and unsupported mutation", flush=True)
        finally:
            stop(process)
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=1):
            raise AssertionError("Artifact listener remained reachable after stopping its process")
    except OSError:
        pass
    cases.append({"case": "artifact-process-and-listener-stopped", "pidStopped": process.pid, "status": "PASS"})
    report = {"scope": "R01c packaged Content bootstrap only; no catalog/publication/DB/auth/mTLS/release qualification",
              "artifact": str(artifact.relative_to(ROOT)), "artifactSha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
              "workingDirectory": str(working_directory.relative_to(ROOT)), "jvmArguments": jvm_arguments, "cases": cases}
    (EVIDENCE / "artifact-smoke.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Content artifact smoke passed: {len(cases)} checks; all Java processes stopped.", flush=True)


if __name__ == "__main__":
    main()
