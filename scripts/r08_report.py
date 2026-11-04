"""Strict R08 named gates; doubles test the gate only, never qualify domain behavior."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def selection(task: str, requested_service: str = "learning") -> dict:
    if task not in {"db", "contract", "fault"} or requested_service not in {"learning", "identity", "content"}:
        raise ValueError("R08_SELECTOR_UNKNOWN")
    if task != "contract" and requested_service != "learning":
        raise ValueError("R08_SELECTOR_UNSUPPORTED")
    profile = json.loads((ROOT / "ops/learning/r08-runtime-selectors.json").read_text(encoding="utf-8"))
    methods = profile["services"][requested_service]
    return {"driverService": "learning", "requestedService": requested_service,
            "methods": methods, "invocations": {name: profile["invocations"][name] for name in methods}}


def verify(target: Path, task: str, requested_service: str = "learning") -> dict:
    selected = selection(task, requested_service)
    required = json.loads((ROOT / "ops/learning/r08-required-cases.json").read_text(encoding="utf-8"))
    if task == "db":
        required.pop("AllocationRuntimeIT")
    else:
        required.pop("AllocationStoreIT")
        required["AllocationRuntimeIT"] = selected["methods"]
    reports = list((target / "surefire-reports").glob("TEST-*.xml"))
    reports += list((target / "failsafe-reports").glob("TEST-*.xml"))
    suites = {}
    for path in reports:
        suite = ET.parse(path).getroot()
        cases = suite.findall("testcase")
        if int(suite.get("tests", "0")) != len(cases) or not cases or any(
            int(suite.get(key, "0")) for key in ("failures", "errors", "skipped")
        ) or any(case.find(tag) is not None for case in cases for tag in ("failure", "error", "skipped")):
            raise ValueError("R08_REPORT_NOT_PASSING")
        name = suite.get("name", "").rsplit(".", 1)[-1]
        if name in suites:
            raise ValueError("R08_DUPLICATE_SUITE")
        suites[name] = suite
    for name, methods in required.items():
        suite = suites.get(name)
        if suite is None:
            raise ValueError("R08_REQUIRED_SUITE_MISSING:" + name)
        names = [case.get("name", "") for case in suite.findall("testcase")]
        for method in methods:
            actual = sum(value == method or value.startswith(method + "(") for value in names)
            expected = selected["invocations"][method] if name == "AllocationRuntimeIT" else 1
            if actual != expected:
                raise ValueError("R08_REQUIRED_CASE_MISSING_OR_DUPLICATED:" + name + ":" + method)
        if name == "AllocationRuntimeIT" and len(names) != sum(selected["invocations"].values()):
            raise ValueError("R08_RUNTIME_SELECTOR_REPORT_DIFFERS")
    return {"status": "PASS", "scope": "R08 " + ("actual allocation DB core" if task == "db"
            else requested_service + " layer via three-service packaged Learning driver"),
            "driverService": "learning", "requestedService": requested_service,
            "suites": len(suites), "runtimeInvocations": 0 if task == "db" else sum(selected["invocations"].values()),
            "requiredCases": sum(map(len, required.values()))}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--task", choices=["db", "contract", "fault"], required=True)
    parser.add_argument("--service", choices=["learning", "identity", "content"], default="learning")
    parser.add_argument("--target", type=Path)
    parser.add_argument("--select", action="store_true")
    args = parser.parse_args()
    if args.select:
        print(json.dumps(selection(args.task, args.service)))
    elif args.target:
        print(json.dumps(verify(args.target, args.task, args.service)))
    else:
        parser.error("--target or --select required")
