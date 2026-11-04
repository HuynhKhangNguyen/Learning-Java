"""Runner gate doubles only; no PostgreSQL/HTTP/journal qualification from these tests."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

SCRIPT = Path(__file__).resolve().parents[1] / "r08_report.py"
SPEC = importlib.util.spec_from_file_location("r08_report", SCRIPT)
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class AllocationReportGateTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.target = Path(self.temporary.name)
        self.folder = self.target / "surefire-reports"
        self.folder.mkdir()
        required = json.loads((GATE.ROOT / "ops/learning/r08-required-cases.json").read_text())
        for name, methods in required.items():
            suite = ET.Element("testsuite", name="lab.backend.learning." + name,
                               tests=str(len(methods)), failures="0", errors="0", skipped="0")
            for method in methods:
                ET.SubElement(suite, "testcase", name=method)
            ET.ElementTree(suite).write(self.folder / ("TEST-" + name + ".xml"))

    def write_runtime_report(self, service="learning"):
        selection = GATE.selection("contract", service)
        methods = selection["methods"]
        invocations = selection["invocations"]
        suite = ET.Element(
            "testsuite",
            name="lab.backend.learning.AllocationRuntimeIT",
            tests=str(sum(invocations.values())),
            failures="0",
            errors="0",
            skipped="0",
        )
        for method in methods:
            for index in range(invocations[method]):
                # Surefire adds the parameter signature/index; the gate binds the base method.
                suffix = "" if invocations[method] == 1 else f"(String)[{index + 1}]"
                ET.SubElement(suite, "testcase", name=method + suffix)
        ET.ElementTree(suite).write(self.folder / "TEST-AllocationRuntimeIT.xml")

    def test_exact_named_report_is_gate_fixture_only(self):
        self.assertEqual(GATE.verify(self.target, "db")["status"], "PASS")

    def test_runtime_manifest_parameter_counts_and_service_subset_are_exact(self):
        for service in ("learning", "identity", "content"):
            with self.subTest(service=service):
                self.write_runtime_report(service)
                result = GATE.verify(self.target, "contract", service)
                selected = GATE.selection("contract", service)
                self.assertEqual(result["requestedService"], service)
                self.assertEqual(
                    result["runtimeInvocations"],
                    sum(selected["invocations"].values()),
                )

    def test_runtime_parameter_count_cannot_be_replaced_by_one_named_case(self):
        self.write_runtime_report()
        report = self.folder / "TEST-AllocationRuntimeIT.xml"
        suite = ET.parse(report).getroot()
        method = next(
            name
            for name, count in GATE.selection("contract")["invocations"].items()
            if count > 1
        )
        duplicate = next(
            case
            for case in suite.findall("testcase")
            if case.get("name", "").startswith(method + "(")
        )
        suite.remove(duplicate)
        suite.set("tests", str(int(suite.get("tests")) - 1))
        ET.ElementTree(suite).write(report)
        with self.assertRaisesRegex(ValueError, "MISSING_OR_DUPLICATED"):
            GATE.verify(self.target, "contract")

    def test_missing_suite_cannot_use_other_nonzero_suites(self):
        (self.folder / "TEST-AllocationStoreIT.xml").unlink()
        with self.assertRaisesRegex(ValueError, "SUITE_MISSING"):
            GATE.verify(self.target, "db")

    def test_missing_case_zero_failure_error_and_skip_fail(self):
        report = self.folder / "TEST-AllocationStoreIT.xml"
        original = report.read_bytes()
        for kind in ("case", "zero", "failures", "errors", "skipped"):
            suite = ET.fromstring(original)
            if kind == "case":
                suite.remove(suite.find("testcase"))
            elif kind == "zero":
                suite.set("tests", "0")
            else:
                suite.set(kind, "1")
            ET.ElementTree(suite).write(report)
            with self.assertRaises(ValueError, msg=kind):
                GATE.verify(self.target, "db")
            report.write_bytes(original)

    def test_database_cases_cannot_be_claimed_as_packaged_runtime(self):
        (self.folder / "TEST-AllocationRuntimeIT.xml").unlink()
        with self.assertRaisesRegex(ValueError, "SUITE_MISSING:AllocationRuntimeIT"):
            GATE.verify(self.target, "contract")

    def test_unknown_or_unsupported_selector_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "SELECTOR_UNSUPPORTED"):
            GATE.selection("fault", "identity")
        with self.assertRaisesRegex(ValueError, "SELECTOR_UNKNOWN"):
            GATE.selection("contract", "unknown")


if __name__ == "__main__":
    unittest.main()
