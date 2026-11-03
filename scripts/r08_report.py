"""Strict named R08 gates; raw XML is evidence, missing/zero/failure/skip never PASS."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def verify(target: Path, task: str) -> dict:
    required = json.loads((ROOT / "ops/learning/r08-required-cases.json").read_text(encoding="utf-8"))
    if task != "db":
        required.pop("AllocationStoreIT")
        required["AllocationRuntimeIT"] = [
            "aaPackagedCreateReadRetryOwnerAndCanonicalAdmissionShapesAreBound",
            "abAnonymousPrivilegedPayloadContextCsrfOriginAndReadBodyCannotDisclose",
            "acActualJournalFailureCrashAfterAckAndRestartRetryKeepSingleAssignment",
        ]
    reports = list((target / "surefire-reports").glob("TEST-*.xml"))
    reports += list((target / "failsafe-reports").glob("TEST-*.xml"))
    suites = {}
    for path in reports:
        suite = ET.parse(path).getroot()
        if int(suite.get("tests", "0")) <= 0 or any(
            int(suite.get(key, "0")) for key in ("failures", "errors", "skipped")
        ):
            raise ValueError("R08_REPORT_NOT_PASSING")
        suites[suite.get("name", "").rsplit(".", 1)[-1]] = suite
    for name, methods in required.items():
        suite = suites.get(name)
        if suite is None:
            raise ValueError("R08_REQUIRED_SUITE_MISSING:" + name)
        names = [case.get("name", "") for case in suite.findall("testcase")]
        if any(method not in names for method in methods):
            raise ValueError("R08_REQUIRED_CASE_MISSING:" + name)
    return {"status": "PASS", "scope": "R08 actual allocation " + ("DB core" if task == "db" else "packaged local runtime"),
            "suites": len(suites), "requiredCases": sum(map(len, required.values()))}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--task", choices=["db", "contract", "fault"], required=True)
    parser.add_argument("--target", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(verify(args.target, args.task)))
