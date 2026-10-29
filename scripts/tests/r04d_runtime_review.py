"""Audit actual canonical three-app baseline; không đổi registry/full leaf DoD."""
from __future__ import annotations

import argparse
from collections import Counter
import errno
import json
from pathlib import Path
import re
import socket
import uuid
import zipfile

from r04d_admission_review import check, digest, suites, UNIT_SUITES, REQUIRED_ADAPTER_CASES, listener_closed

ROOT = Path(__file__).resolve().parents[2]
SERVICES = ("identity", "content", "learning")
RUNTIME_SUITE = "lab.backend.learning.identityaccess.CanonicalThreeServiceRuntimeIT"
RUNTIME_CASES = {
    "aaCanonicalProviderDrivesBothOwnAccessHooks": 1,
    "abMissingReceiverKeepsPendingAndRestartCompletesExactBarrier": 1,
    "acPublicProofAndLearningOnlyReservationAreDurablyBound": 1,
    "adActualMutationGateDelaysFenceAndCompletionUntilCommitOrRollback": 2,
    "aeLostAckAndBothProcessRestartsReuseExactDurableCommand": 1,
    "afActualSensitiveProofAndAccountBarrierSurviveRestart": 2,
    "agCanonicalProviderRejectsCallerContextCsrfAndRequestBindings": 1,
    "ahLearningReservationRejectsEveryBindingBeforeAndAfterRestart": 1,
    "aiSensitiveProofRejectsCrossPurposePayloadAndEpochOverTls": 2,
    "ajWrongAuthenticatedAckCannotCompleteOrBlockIndependentBarrier": 2,
    "akWrongReceiverCertificateCannotCompleteBarrier": 2,
    "alCanonicalCompletionPassesStrictPoisonWithoutClearingItsStream": 1,
    "amLegacyCredentialReconciliationUsesActualWorkerAndKeepsLifecycleGate": 2,
    "azActualSensitiveBrowserCookieReorderAndMultiTab": 2,
}
# Mapping owner layer phải khớp helper. Selector không phải qualification
# toàn baseline; cổng vẫn buộc exact variants và không nhận case ngoài selector.
RUNTIME_SELECTORS = {
    "SEC-01": ("adActualMutationGateDelaysFenceAndCompletionUntilCommitOrRollback",),
    "SEC-03": ("adActualMutationGateDelaysFenceAndCompletionUntilCommitOrRollback",),
    "SEC-05": ("aaCanonicalProviderDrivesBothOwnAccessHooks", "abMissingReceiverKeepsPendingAndRestartCompletesExactBarrier"),
    "SEC-06": ("abMissingReceiverKeepsPendingAndRestartCompletesExactBarrier", "afActualSensitiveProofAndAccountBarrierSurviveRestart", "azActualSensitiveBrowserCookieReorderAndMultiTab"),
    "SEC-07": ("aeLostAckAndBothProcessRestartsReuseExactDurableCommand", "afActualSensitiveProofAndAccountBarrierSurviveRestart"),
    "SEC-08": ("ajWrongAuthenticatedAckCannotCompleteOrBlockIndependentBarrier",),
    "SEC-11": ("azActualSensitiveBrowserCookieReorderAndMultiTab",),
    "SEC-12": ("amLegacyCredentialReconciliationUsesActualWorkerAndKeepsLifecycleGate",),
    "SEC-19": ("agCanonicalProviderRejectsCallerContextCsrfAndRequestBindings", "aiSensitiveProofRejectsCrossPurposePayloadAndEpochOverTls", "azActualSensitiveBrowserCookieReorderAndMultiTab"),
    "SEC-20": ("agCanonicalProviderRejectsCallerContextCsrfAndRequestBindings", "akWrongReceiverCertificateCannotCompleteBarrier"),
    "SEC-23": ("acPublicProofAndLearningOnlyReservationAreDurablyBound", "ahLearningReservationRejectsEveryBindingBeforeAndAfterRestart", "aiSensitiveProofRejectsCrossPurposePayloadAndEpochOverTls", "azActualSensitiveBrowserCookieReorderAndMultiTab"),
    "DATA-15": ("alCanonicalCompletionPassesStrictPoisonWithoutClearingItsStream",),
}
BROWSER_CASES = {
    "browser-A-active", "host-secure-httponly-cookie", "browser-js-cannot-read-cookie",
    "second-tab-A-binding", "natural-origin-sensitive-proof", "pending-without-cookie",
    "pending-bootstrap-locked", "pending-blocks-new-credential-flow",
    "browser-B-independent-owner", "late-A-no-cookie-B-survives", "cross-cookie-retry-denied",
    "cross-cookie-status-denied", "second-tab-observes-B", "old-CSRF-cannot-mint-under-B",
    "Back-focus-no-cookie-renewal", "initiating-status-completed-only", "wrong-context-status-denied",
    "original-bound-sensitive-retry-completed", "old-A-cookie-never-resurrects",
    "completed-A-does-not-revoke-B", "foreign-origin-denied",
    "no-status-proof-sensitive-cookie-emission", "no-browser-runtime-errors",
}


def browser_cases(report: dict, purpose: str) -> None:
    """Exact redacted browser report, không summary/count thay ca còn thiếu."""
    check(set(report) == {"stage", "status", "purpose", "checks", "cases", "browser", "version", "origin", "scope"},
          "Browser fields missing/unexpected")
    check(report["stage"] == "RESULT" and report["status"] == "PASS" and report["purpose"] == purpose,
          "Browser actual purpose/result not passing")
    check(isinstance(report["cases"], list) and all(isinstance(label, str) for label in report["cases"]), "Invalid browser cases")
    check(len(report["cases"]) == len(BROWSER_CASES) and set(report["cases"]) == BROWSER_CASES
          and type(report["checks"]) is int and report["checks"] == len(BROWSER_CASES), "Required browser case missing/duplicated")
    check(report["origin"] == "https://localhost:17441" and report["browser"] == "installed Microsoft Edge / Chromium"
          and re.fullmatch(r"\d+\.\d+\.\d+\.\d+", report["version"]) is not None, "Wrong browser/named topology")


def runtime_cases(names: list[str], selector: str | None = None) -> None:
    """Chặn tên gần giống/variant trùng; hai gate variants không là một case."""
    check(selector is None or selector in RUNTIME_SELECTORS, "Unknown canonical selector")
    required = RUNTIME_CASES if selector is None else {name: RUNTIME_CASES[name] for name in RUNTIME_SELECTORS[selector]}
    expected = sum(required.values())
    check(len(names) == expected and len(set(names)) == expected, "Canonical baseline needs all unique required cases")
    methods = []
    for name in names:
        match = re.fullmatch(r"([A-Za-z0-9]+)(?:\([^)]*\))?(?:\[\d+\])?", name)
        check(match is not None, "Invalid runtime case name")
        methods.append(match.group(1))
    check(Counter(methods) == Counter(required), "Required runtime case/variant missing or unexpected")


def expected_inputs() -> set[str]:
    """Tự dựng inventory từ source thật, không tin snapshot bị cắt là chuẩn."""
    paths = {ROOT / path for path in (
        "backend/pom.xml", "scripts/services.ps1", "scripts/db_lab.py",
        "scripts/trust_lab.py", "scripts/tests/session_browser.mjs", "ops/db-lab/layout.json",
        "scripts/tests/canonical_session_browser.mjs", "frontend/package.json", "frontend/package-lock.json",
        "backend/mvnw", "backend/mvnw.cmd", "backend/.mvn/wrapper/maven-wrapper.properties",
        "ops/release/dependencies.json", "ops/release/postgresql.json",
        "scripts/tests/backend-build.Tests.ps1", "scripts/tests/r04d_admission_review.py",
        "scripts/tests/r04d_runtime_review.py", "scripts/tests/test_r04d_runtime_review.py",
    )}
    directories = []
    for service in SERVICES:
        base = ROOT / "backend/services" / (service + "-service")
        paths.add(base / "pom.xml")
        directories.extend((base / "src", ROOT / "ops" / service))
    directories.extend(ROOT / "contracts/http" / contract for contract in ("identity", "admission", "delivery", "common"))
    for directory in directories:
        paths.update(path for path in directory.rglob("*") if path.is_file())
    return {str(path.relative_to(ROOT)) for path in paths}


def required_units(service: str) -> set[str]:
    if service == "identity":
        return {"lab.backend.identity." + name for name in UNIT_SUITES}
    return {"lab.backend." + service + "." + name for name in (
        service.title() + "BootstrapConfigTests", service.title() + "BootstrapHttpTests",
        "delivery.DeliveryWireTests", "delivery.DeliveryRuntimeConfigTests",
        "delivery.DeliveryHttpHandlerTests", "identityaccess.AdmissionWireTests",
    )}


def report_totals(reports: dict, summary: dict) -> None:
    check(summary == {"tests": sum(suite["tests"] for suite in reports.values()),
                      "failures": 0, "errors": 0, "skipped": 0}, "Runtime report/summary totals differ")


def artifact(service: str, evidence: Path, summary: dict) -> str:
    """Đối chiếu retained/current ZIP và own compiled classes/migration bytes.

    Compile report/input snapshot là bằng chứng nguồn; so bytes chống target JAR
    cũ hoặc fixture lọt package. Không suy production qualification từ JAR xanh.
    """
    base = ROOT / "backend/services" / (service + "-service")
    retained = evidence / service / "qualified-artifact.jar"
    artifact_hash = digest(retained)
    check(artifact_hash == summary["artifactSha256"], "Retained artifact hash differs")
    check(digest(base / "target" / (service + "-service-0.1.0-SNAPSHOT.jar")) == artifact_hash,
          "Current artifact differs from qualified runtime")
    with zipfile.ZipFile(retained) as jar:
        names = jar.namelist()
        check(len(names) == len(set(names)), "Duplicate runtime ZIP entry")
        check(not any(name.endswith(".p12") or "CanonicalAccessFixtureMain" in name or "CanonicalFenceFaultPeer" in name
                      or "CanonicalThreeServiceRuntimeIT" in name or "/db-test-" in name
                      or any("/" + lib + "-" in name for lib in ("flyway", "junit", "mockito", "assertj"))
                      for name in names), "Test-only/secret material packaged")
        check(not any(name.startswith("BOOT-INF/lib/") and any(peer + "-service" in name for peer in SERVICES)
                      for name in names), "Shared/cross-service domain JAR packaged")
        own_prefix = "BOOT-INF/classes/lab/backend/" + service + "/"
        check(any(name.startswith(own_prefix) for name in names), "Own runtime classes absent")
        for other in SERVICES:
            if other != service:
                check(not any(name.startswith("BOOT-INF/classes/lab/backend/" + other + "/") for name in names),
                      "Cross-service domain classes packaged")
        compiled = base / "target/classes"
        for source_class in (compiled / "lab/backend" / service).rglob("*.class"):
            check(jar.read("BOOT-INF/classes/" + source_class.relative_to(compiled).as_posix()) == source_class.read_bytes(),
                  "Packaged/compiled own class differs")
        for migration in (base / "src/main/resources/db/migration").glob("*.sql"):
            check(jar.read("BOOT-INF/classes/db/migration/" + migration.name) == migration.read_bytes(),
                  "Own migration bytes differ")
    return artifact_hash


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run", required=True)
    args = parser.parse_args()
    check(str(uuid.UUID(args.run)) == args.run, "Canonical run UUID required")
    evidence = ROOT / "artifacts/quality/R04d" / args.run
    check(evidence.resolve() == evidence, "Evidence resolves through a link")
    report = json.loads((evidence / "report.json").read_text(encoding="utf-8"))
    selector = report["case"]
    check(report["status"] == "PASS" and report["service"] == "learning"
          and ((report["task"] == "contract" and selector is None)
               or (report["task"] == "fault" and selector in RUNTIME_SELECTORS))
          and report["clusterStopped"] is True and report["runId"] == args.run,
          "Required actual canonical run not passing/stopped")
    check(report["runtime"] == "postgres (PostgreSQL) 18.6", "Wrong PostgreSQL runtime")
    lab = ROOT / report["labDirectory"]
    check(lab.resolve() == lab and lab.parent == ROOT / ".tools/db-lab", "Wrong owned lab path")
    check(json.loads((lab / "ownership.json").read_text(encoding="utf-8")) == {
        "schemaVersion": 1, "runId": args.run, "workspace": str(ROOT), "dataDirectory": str(lab / "data")},
        "Ownership mismatch")
    # Read-only probe; không stop PID/kill process theo port/report không đáng tin.
    ports = [report["port"], 18081, 18082, 18083, 18441, 18442, 18443, 17441]
    for port in ports:
        listener_closed(port)
    inputs = json.loads((evidence / "learning-inputs.json").read_text(encoding="utf-8"))
    check(set(inputs) == expected_inputs(), "Canonical whole-input inventory differs")
    for path, expected in inputs.items():
        source = ROOT / path
        check(source.resolve().is_relative_to(ROOT) and source.is_file(), "Input missing/outside workspace")
        check(digest(source) == expected, "Current input differs from runtime: " + path)
    outcomes = report["services"]
    check(len(outcomes) == 1 and outcomes[0]["service"] == "learning" and outcomes[0]["status"] == "PASS",
          "Wrong canonical driver outcome")
    summaries = {item["service"]: item for item in outcomes[0]["peers"]}
    check(len(outcomes[0]["peers"]) == 2 and set(summaries) == {"identity", "content"}, "Required peer artifacts missing")
    summaries["learning"] = outcomes[0]
    integration = suites(evidence / "learning/failsafe-reports")
    check(set(integration) == {RUNTIME_SUITE}, "Wrong canonical runtime suite set")
    runtime_cases(integration[RUNTIME_SUITE]["cases"], selector)
    report_totals(integration, summaries["learning"]["reports"]["canonical-runtime"])
    browsers = {}
    browser_required = selector is None or "azActualSensitiveBrowserCookieReorderAndMultiTab" in RUNTIME_SELECTORS[selector]
    for purpose in (("LOGOUT_ALL", "PASSWORD_CHANGE") if browser_required else ()):
        browser_path = evidence / ("canonical-browser-" + purpose + ".json")
        check(browser_path.is_file(), "Required actual canonical browser report missing")
        browsers[purpose] = json.loads(browser_path.read_text(encoding="utf-8"))
        browser_cases(browsers[purpose], purpose)
    artifacts, units = {}, {}
    for service in SERVICES:
        units[service] = suites(evidence / service / "surefire-reports")
        check(set(units[service]) == required_units(service), "Required own unit suite set differs: " + service)
        # Missing named canonical config/adapter case không được che bằng count
        # units cũ; mocks chỉ input boundary, không thay runtime/socket evidence.
        config = units[service]["lab.backend." + service + ".delivery.DeliveryRuntimeConfigTests"]
        check({"namedCanonicalTopologyKeepsStaticPortsAndLegacyDefaults",
               "canonicalTransportRejectsArbitraryListenerBeforeOpeningDatabase"}.issubset(set(config["cases"])),
              "Named canonical config case missing: " + service)
        if service == "identity":
            for name, required in REQUIRED_ADAPTER_CASES.items():
                check(required.issubset(set(units[service]["lab.backend.identity." + name]["cases"])),
                      "Required canonical adapter/config case missing: " + name)
        if service == "learning":
            # Product origin loader đổi chỉ cho named canonical browser. Phải
            # giữ legacy denial và mọi arbitrary endpoint negative, không chỉ
            # fresh browser happy-path hoặc số unit tổng lớn hơn.
            required_origin = {"canonicalNamedBrowserOriginKeepsLegacyPolicy", "legacyProfileCannotEnableCanonicalBrowserOrigin"}
            required_origin.update(f"canonicalOriginCannotBeAnArbitraryEndpoint(String)[{i}]" for i in range(1, 8))
            check(required_origin.issubset(set(units[service]["lab.backend.learning.identityaccess.AdmissionWireTests"]["cases"])),
                  "Required named browser/legacy Origin policy case missing")
        report_totals(units[service], summaries[service]["reports"]["bootstrap"])
        artifacts[service] = artifact(service, evidence, summaries[service])
        check((evidence / (service + "-canonical.log")).is_file(), "Actual app log missing")
    manifest = json.loads((ROOT / "ops/release/dependencies.json").read_text(encoding="utf-8"))
    for service in SERVICES:
        item = manifest["services"][service]
        check(item["workingCandidate"]["task"] == "BL-R04d" and item["workingCandidate"]["state"] == "IN_PROGRESS",
              "Working candidate state drift")
        check(item["qualifiedDeliveryCandidate"]["task"] == "BL-R02c"
              and item["qualifiedDeliveryCandidate"]["state"] == "DONE", "Qualified R02c history lost")
    result = {"status": "PASS", "runId": args.run, "case": selector,
              "scope": "Actual three-app canonical " + ("baseline" if selector is None else "selected fault layer " + selector),
              "limits": "Baseline/selector only; remaining selected owner layers/full closing suites and AC01-07 final DoD remain required",
              "inputFiles": len(inputs), "reportSha256": digest(evidence / "report.json"),
              "artifacts": artifacts, "integration": integration, "units": units, "browsers": browsers, "ownedListenersClosed": ports}
    (evidence / "runtime-review.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: result[key] for key in ("status", "runId", "scope", "limits", "inputFiles")}))


if __name__ == "__main__":
    main()
