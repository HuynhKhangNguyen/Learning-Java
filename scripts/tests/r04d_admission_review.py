"""Audit R04d canonical admission own-PG layer; không là full leaf/SEC DoD."""
from __future__ import annotations

import argparse
import errno
import hashlib
import json
from pathlib import Path
import socket
import uuid
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
REQUIRED_CASES = {
    "canonicalReadBindsFreshNonceOwnerSessionRealmAndDeadlinesWithoutIdleTouch",
    "canonicalDraftSaveVerifiesSynchronizerAndTouchesOnlyItsOwnSession",
    "canonicalBadCsrfAndContextDoNotTouchIdleOrCreateOperation",
    "canonicalUnknownCookieAndPreauthCannotCreateLearnerAdmission",
    "canonicalExpiredMutationCommitsDenyAndCannotRepeatExpiryIntents",
    "canonicalAbsoluteExpiryAndOrphanBackingAreDeniedDurably",
    "canonicalLogoutFencePendingAlreadyDeniesAdmissionWithoutWaitingForAck",
    "canonicalShortIdleBudgetDeniesReadWithoutRenewingIt",
    "canonicalWrongRealmFailsClosedWithoutTouchOrLocalExpiry",
    "canonicalBlockedAdmissionRereadsCommittedAccountTransition",
    "canonicalExpiryIntentFailureRollsBackSessionAndOperation",
    "canonicalReadDeadlineIsCappedByIdleAndAbsoluteIndependently",
    "canonicalMutationInsufficientAbsoluteBudgetRollsBackIdleTouch",
}
REQUIRED_BARRIER_CASES = {
    "barrierPendingTransitionScanPassesSeventeenPoisonIds",
    "barrierPendingReconcileScanPassesSeventeenPoisonIds",
    "barrierPendingCompletionScanPassesSeventeenPoisonIds",
    "barrierExpiryCommitsTwoCommandsAndCompletedFenceAllowsFreshFlow",
    "barrierCompletedLogoutAllowsFreshFlowAndOldRetryCannotRevokeNewSession",
    "barrierTerminalSessionWithoutCompletedFenceCannotCreateFreshFlow",
    "barrierResetAfterEnableCannotBypassPendingLifecycle",
    "barrierLogoutAtomicCommandsAndBothAcksAreRequired",
    "barrierSecondOutboxFailureRollsBackCanonicalDeny",
    "barrierLegacyReconciliationPreservesOperationAndExactBytes",
    "barrierPendingCannotClearOrCompleteByDirectSql",
    "barrierResetCurrentEpochCompletesAndFreshLoginUsesNewEpoch",
    "barrierOlderResetAckNeverClearsNewerTransition",
    "barrierCurrentGenerationChangeRejectsOldAckWithoutRetargeting",
    "barrierCompletionFailureRollsBackOperationAndCanonicalSession",
    "barrierTwoConcurrentCompletersProduceOneDurableEffect",
    "barrierLegacyCredentialResetReconciliationIsImmutableAndDigestOnly",
    "barrierDisableAndEnableAuthCompletionKeepLifecycleGate",
    "barrierRuntimeVerificationCannotRepairMissingCompletionGuard",
}
REQUIRED_REAUTH_CASES = {
    "reauthDigestOnlySameIntentReservationHasFixedReferenceAndTtl",
    "reauthCallerAndEveryIntentBindingAreRequired",
    "reauthBadCsrfAndContextCannotReachKdfOrThrottle",
    "reauthWrongPasswordHasDedicatedThrottleAndCannotMint",
    "reauthMintFailureRollsBackGrantAndIdle",
    "reauthReservationRollbackAndRestartCannotRefundOrRetarget",
    "reauthExpiredGrantAndChangedEpochAreDenied",
    "reauthKdfLogoutRaceCannotMintFromOldSession",
    "reauthRuntimeGuardAndImmutablePrivilegesFailClosed",
    "reauthConcurrentSameIntentHasOneReservation",
    "reauthKdfEpochChangeRaceCannotMintFromOldCredential",
    "reauthKdfExpiryRaceCommitsDenyWithoutMintingProof",
    "reauthConcurrentCapacityCannotOvershootGlobalBound",
}
DB_SUITES = {
    "database.DatabaseIsolationIT",
    "credential.IdentityCredentialWorkflowIT",
    "credential.CredentialPackagedIT",
    "credential.IdentitySessionAuthorityIT",
    "delivery.DeliveryStoreIT",
}
REQUIRED_SENSITIVE_CASES = {
    "sensitiveLogoutAllAtomicallyDeniesAllSessionsAndRetriesWithoutIdleOrCookieEffect",
    "sensitivePasswordChangeBindsPayloadAndHashesOnlyOneDurableEffect",
    "sensitiveEveryBindingAndLearningPurposeAreRequired",
    "sensitiveSecondOutboxFailureRollsBackProofHashEpochTokensAndDeny",
    "sensitiveAuditFailureRollsBackConsumeAndOutboxes",
    "sensitiveConcurrentConsumeAndRestartReturnOneImmutableOperation",
    "sensitiveDirectConsumeCannotCommitWithoutAtomicEffect",
    "sensitiveMissingDeferredGuardAndChangedStorageKeyFailClosed",
    "sensitivePasswordKdfResetRaceCannotConsumeOldProof",
    "sensitiveExpiredProofAndCrossPurposeReservationCannotMutate",
}
UNIT_SUITES = {
    "credential.CredentialPolicyTests", "credential.KdfBenchmarkTests",
    "delivery.AuthDeliveryWireTests", "delivery.DeliveryHttpHandlerTests",
    "delivery.DeliveryRuntimeConfigTests", "delivery.DeliveryWireTests",
    "IdentityBootstrapConfigTests", "IdentityBootstrapHttpTests",
    "session.CanonicalAdmissionWireTests", "session.SessionHttpHandlerTests",
    "session.ReauthReservationWireTests", "session.CanonicalAuthHttpHandlerTests",
    "session.IdentitySensitiveIntentTests",
    "session.AuthPendingWorkTests",
}
REQUIRED_ADAPTER_CASES = {
    # Scanner units có exact từng phase variant; không đếm ba variants thành
    # một case hoặc dùng JDBC doubles thay actual own-PG fairness proof.
    "session.AuthPendingWorkTests": {
        "poisonBatchCannotHideLaterWork(Phase)[1]", "poisonBatchCannotHideLaterWork(Phase)[2]",
        "poisonBatchCannotHideLaterWork(Phase)[3]", "phasesKeepIndependentWindows",
        "arrivalsWaitForNextBoundedRound", "disappearedTailEndsRoundWithoutSkippingNextRound",
        "sqlFailureDoesNotAdvanceOrRetargetWindow", "snapshotClosesBeforeEffectsAndCannotBeRewritten",
        "emptyAndMissingInputsCannotInventPendingWork",
    },
    "credential.CredentialPolicyTests": {
        "sensitivePreparationChecksPolicyAndCorpusBeforeKdf",
        "sensitivePreparationIsBoundedAndReleasesSingletonSlots",
        "sensitivePreparationKdfFailureDoesNotLeakOrKeepSlot",
    },
    "session.IdentitySensitiveIntentTests": {
        "independentCanonicalVectorsBindExactUtf8Command", "independentStorageVectorsDoNotPersistPasswordDerivedSha",
        "contextKeyAndPasswordChangesCannotReuseIntent", "newlinePayloadUsesLengthFramingNotFieldSeparators",
        "unknownActionMissingBindingAndHiddenPasswordAreRejected", "secretDomainKeyRemainsStableAndDifferentKeysDenyRetarget",
        "invalidKeyAndNonCanonicalDigestAreRejectedWithoutSecretEcho",
    },
    "session.CanonicalAuthHttpHandlerTests": {
        "int01PassesExactBodyOnlyToCanonicalAuthority", "int06ParsesBindingAndReturnsOnlyDurableReference",
        "publicPlaintextQueryAndWrongMethodCannotReachAuthority", "duplicateContentTypeOversizeAndBadJsonDenyBeforeReserve",
        "dependencyFailureAndCanonicalDenyNeverEchoCredentials",
    },
    "session.ReauthReservationWireTests": {
        "exactBindingAndBigEpochSurviveWithoutLoggingProof", "strictJsonAndByteLimitCannotBeBypassed",
        "reservedResponseHasFixedBindingNoRawProofAndFlooredTtl", "numberEpochUnknownKeysPurposeAndHashAreDenied",
    },
    "session.SessionHttpHandlerTests": {
        "explicitReauthPassesPasswordOnlyToIdentityAndNeverIssuesCookie",
        "oldRuntimeCannotEnableReauthOrPasswordOnlyFallback", "reauthDependencyAndCanonicalCsrfDenyAreRedacted",
        "canonicalLogoutRetryReportsActualCompletedWithoutCookie",
        "canonicalPublicHandlerCannotAcceptLegacyOrArbitraryPort",
        "canonicalSensitiveReauthUsesBoundPurposeWithoutCookie(String)[1]",
        "canonicalSensitiveReauthUsesBoundPurposeWithoutCookie(String)[2]",
        "canonicalSensitiveCommandsUseProofOnlyAndStatusWithoutBootstrap(String)[1]",
        "canonicalSensitiveCommandsUseProofOnlyAndStatusWithoutBootstrap(String)[2]",
        "canonicalSensitiveBoundaryRejectsBeforeCore(String)[1]",
        "canonicalSensitiveBoundaryRejectsBeforeCore(String)[2]",
        "canonicalSensitiveBoundaryRejectsBeforeCore(String)[3]",
        "canonicalSensitiveBoundaryRejectsBeforeCore(String)[4]",
        "canonicalSensitiveBoundaryRejectsBeforeCore(String)[5]",
        "canonicalSensitiveBoundaryRejectsBeforeCore(String)[6]",
        "canonicalSensitiveBoundaryRejectsBeforeCore(String)[7]",
        "canonicalSensitiveCoreDenyAndStorageFailureAreRedacted",
        "canonicalSensitiveCompositionCannotEnableStandaloneOrMissingProofOwner",
        "canonicalSensitiveRetryReturnsCompletedWithoutClearingCookie",
    },
    "delivery.DeliveryRuntimeConfigTests": {
        "namedCanonicalTopologyKeepsStaticPortsAndLegacyDefaults",
        "canonicalTransportRejectsArbitraryListenerBeforeOpeningDatabase",
        "canonicalValidConfigCannotFakeMissingOwnDatabaseReadiness",
    },
}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check(condition: bool, message: str) -> None:
    # Không dùng assert: python -O không được phép bỏ các gate qualification.
    if not condition:
        raise RuntimeError(message)


def suites(directory: Path) -> dict:
    """Đọc XML actual; count bằng testcase, không tin mỗi con số summary."""
    reports = sorted(directory.glob("TEST-*.xml"))
    check(bool(reports), "Required reports missing")
    result = {}
    for report in reports:
        node = ET.parse(report).getroot()
        check(node.tag == "testsuite", "Unsupported JUnit report")
        counts = {key: int(node.attrib[key]) for key in ("tests", "failures", "errors", "skipped")}
        cases = node.findall("testcase")
        check(counts["tests"] > 0 and counts["tests"] == len(cases), "Missing/zero/mismatched testcase count")
        check(not any(counts[key] for key in ("failures", "errors", "skipped")), "Non-passing required suite")
        check(not any(case.find(tag) is not None for case in cases for tag in ("failure", "error", "skipped")),
              "Testcase contains failure/error/skip")
        check(node.attrib["name"] not in result, "Duplicate suite")
        result[node.attrib["name"]] = {
            "tests": counts["tests"], "cases": [case.attrib["name"] for case in cases], "sha256": digest(report)
        }
    return result


def core_layer(task: str, case: str | None) -> bool:
    """CMD03 baseline hoặc named CMD05 full-core bundle, không legacy layer."""
    return (task == "db" and case is None) or (task == "fault" and case in {"SEC-10", "SEC-12"})


def require_core_inventory(inputs: dict, task: str, case: str | None) -> None:
    """Kiểm exact inventory theo layer; CMD05 còn snapshot common contract.

    Không lấy chính danh sách đã nhận làm chuẩn: tự quét source/config hiện tại
    để phát hiện file thiếu/thừa. Khác biệt CMD03/CMD05 theo db_lab.py, không
    phải quyền bỏ hash check hoặc nhận tùy ý file ngoài phạm vi own-core.
    """
    check(core_layer(task, case), "Unsupported own-core inventory layer")
    expected = {ROOT / name for name in (
        "backend/pom.xml", "backend/services/identity-service/pom.xml",
        "scripts/services.ps1", "scripts/db_lab.py", "ops/db-lab/layout.json",
        "scripts/tests/session_browser.mjs",
    )}
    directories = [ROOT / "backend/services/identity-service/src", ROOT / "ops/identity",
                   ROOT / "contracts/http/identity"]
    if task == "fault":
        directories.append(ROOT / "contracts/http/common")
    for directory in directories:
        expected.update(path for path in directory.rglob("*") if path.is_file())
    check({str(path.relative_to(ROOT)) for path in expected} == set(inputs),
          "Source/config/helper inventory differs from own-PG run")


def listener_closed(port: int) -> None:
    """Chỉ ConnectionRefused là closed proof; timeout/permission vẫn fail."""
    with socket.socket() as probe:
        # Windows actual lab cần hơn1s để trả WSAECONNREFUSED. Budget3s chỉ
        # cho read-only audit sau cleanup, không đổi HTTP/SQL/service timeout.
        probe.settimeout(3)
        check(probe.connect_ex(("127.0.0.1", port)) in {errno.ECONNREFUSED, 10061},
              "Owned listener closure unproven")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run", required=True)
    args = parser.parse_args()
    check(str(uuid.UUID(args.run)) == args.run, "Canonical owned run UUID required")
    evidence = ROOT / "artifacts/quality/R04d" / args.run
    check(evidence.resolve() == evidence, "Evidence resolves through a link")
    report = json.loads((evidence / "report.json").read_text(encoding="utf-8"))
    check(report["status"] == "PASS" and report["service"] == "identity"
          and core_layer(report["task"], report["case"])
          and report["runId"] == args.run and report["clusterStopped"] is True,
          "Required actual owned Identity DB run not passing/stopped")
    check(report["runtime"] == "postgres (PostgreSQL) 18.6", "Wrong qualified PostgreSQL runtime")
    check(len(report["services"]) == 1 and report["services"][0]["service"] == "identity"
          and report["services"][0]["status"] == "PASS", "Wrong selected service outcomes")
    # Marker ràng cluster vào workspace/run; chỉ đọc và probe port, không stop PID
    # hay xóa folder dựa trên report không đáng tin.
    lab = ROOT / report["labDirectory"]
    check(lab.resolve() == lab and lab.parent == ROOT / ".tools/db-lab", "Wrong owned lab path")
    marker = json.loads((lab / "ownership.json").read_text(encoding="utf-8"))
    check(marker == {"schemaVersion": 1, "runId": args.run, "workspace": str(ROOT),
                     "dataDirectory": str(lab / "data")}, "Ownership marker mismatch")
    listener_closed(report["port"])

    inputs = json.loads((evidence / "identity-inputs.json").read_text(encoding="utf-8"))
    for name, expected in inputs.items():
        path = ROOT / name
        check(path.resolve().is_relative_to(ROOT) and path.is_file(), "Input outside workspace/missing")
        check(digest(path) == expected, "Current input differs from qualified own-PG run: " + name)
    # Mỗi layer giữ exact inventory riêng; fault snapshot thêm common contract
    # dù vẫn chạy full own-core DB bundle, không chạy trust/browser profile.
    source = ROOT / "backend/services/identity-service/src"
    require_core_inventory(inputs, report["task"], report["case"])
    integration = suites(evidence / "identity/failsafe-reports")
    units = suites(evidence / "identity/surefire-reports")
    check(set(integration) == {"lab.backend.identity." + name for name in DB_SUITES}, "Wrong required DB suite set")
    check(set(units) == {"lab.backend.identity." + name for name in UNIT_SUITES}, "Wrong required unit suite set")
    canonical = integration["lab.backend.identity.credential.IdentitySessionAuthorityIT"]
    check(REQUIRED_CASES.issubset(set(canonical["cases"])), "Required canonical admission case missing")
    check(REQUIRED_BARRIER_CASES.issubset(set(canonical["cases"])), "Required canonical barrier case missing")
    check(REQUIRED_REAUTH_CASES.issubset(set(canonical["cases"])), "Required own-PG reauth core case missing")
    check(REQUIRED_SENSITIVE_CASES.issubset(set(canonical["cases"])), "Required own-PG Identity sensitive case missing")
    check(units["lab.backend.identity.session.CanonicalAdmissionWireTests"]["tests"] == 44, "Wrong parser case count")
    for name, required in REQUIRED_ADAPTER_CASES.items():
        check(required.issubset(set(units["lab.backend.identity." + name]["cases"])),
              "Required canonical HTTP/wire adapter case missing: " + name)
    for kind, data in (("database", integration), ("bootstrap", units)):
        check(sum(item["tests"] for item in data.values()) == report["services"][0]["reports"][kind]["tests"],
              "Summary/report total mismatch")
    artifact = evidence / "identity/qualified-artifact.jar"
    check(digest(artifact) == report["services"][0]["artifactSha256"], "Retained artifact hash mismatch")
    current = ROOT / "backend/services/identity-service/target/identity-service-0.1.0-SNAPSHOT.jar"
    check(digest(current) == digest(artifact), "Current artifact differs from this own-PG candidate")
    with zipfile.ZipFile(artifact) as jar:
        names = jar.namelist()
        check("BOOT-INF/classes/lab/backend/identity/session/CanonicalAdmissionWire.class" in names
              and "BOOT-INF/classes/lab/backend/identity/session/SessionAuthority$Admission.class" in names
              and "BOOT-INF/classes/lab/backend/identity/session/AuthBarrierCoordinator.class" in names
              and "BOOT-INF/classes/lab/backend/identity/session/ReauthAuthority.class" in names
              and "BOOT-INF/classes/lab/backend/identity/session/CanonicalAuthHttpHandler.class" in names
              and "BOOT-INF/classes/lab/backend/identity/session/ReauthReservationWire.class" in names
              and "BOOT-INF/classes/lab/backend/identity/session/CanonicalRuntime.class" in names
              and "BOOT-INF/classes/lab/backend/identity/session/CanonicalConfiguration.class" in names,
              "Canonical source classes absent from packaged artifact")
        check(not any(name.endswith(".p12") or "/db-test-" in name or "IdentitySessionAuthorityIT" in name
                      or "/flyway-" in name or "/junit-" in name or "/mockito-" in name for name in names),
              "Test-only/secret material in runtime artifact")
        for migration in (source / "main/resources/db/migration").glob("*.sql"):
            check(jar.read("BOOT-INF/classes/db/migration/" + migration.name) == migration.read_bytes(),
                  "Own migration bytes differ from packaged artifact")
    manifest = json.loads((ROOT / "ops/release/dependencies.json").read_text(encoding="utf-8"))
    for service in manifest["services"].values():
        check(service["workingCandidate"]["task"] == "BL-R04d" and service["workingCandidate"]["state"] == "IN_PROGRESS",
              "Working candidate state drift")
        check(service["qualifiedDeliveryCandidate"]["task"] == "BL-R02c"
              and service["qualifiedDeliveryCandidate"]["state"] == "DONE", "Qualified R02c history lost")
    # Report nói rõ lớp đã chứng minh; không đổi registry/Goal hoặc gắn full SEC PASS.
    result = {"status": "PASS", "case": report["case"], "scope": "R04d canonical admission/auth barrier/reauth full core own-PG/current artifact layer only",
              "limits": "ACK bytes fixtures, not remote receiver commits; no canonical mTLS/reauth HTTP/INT06 wire/three-app/browser or full leaf DoD proof",
              "runId": args.run, "inputFiles": len(inputs), "reportSha256": digest(evidence / "report.json"),
              "artifactSha256": digest(artifact), "integration": integration, "units": units,
              "requiredCanonicalCases": len(REQUIRED_CASES), "requiredBarrierCases": len(REQUIRED_BARRIER_CASES), "requiredReauthCases": len(REQUIRED_REAUTH_CASES), "requiredSensitiveCases": len(REQUIRED_SENSITIVE_CASES), "ownedDbListenerClosed": True}
    (evidence / "admission-review.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: result[key] for key in ("status", "scope", "limits", "runId", "inputFiles", "requiredCanonicalCases", "requiredBarrierCases", "requiredReauthCases")}))


if __name__ == "__main__":
    main()
