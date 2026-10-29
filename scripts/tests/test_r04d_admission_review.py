"""Report-gate unit fixtures; không là actual PostgreSQL/canonical admission proof."""
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import r04d_admission_review as review


class AdmissionReviewTests(unittest.TestCase):
    """Kiểm gate chống green giả khi summary khác testcase hoặc thiếu suite."""

    def test_core_layer_accepts_only_baseline_or_implemented_full_core_fault(self):
        for task, case in (("db", None), ("fault", "SEC-10"), ("fault", "SEC-12")):
            self.assertTrue(review.core_layer(task, case))
        for task, case in (("contract", None), ("db", "SEC-12"), ("fault", None),
                           ("fault", "SEC-11"), ("fault", "SEC-22"), ("fault", "DATA-15")):
            with self.subTest(task=task, case=case):
                self.assertFalse(review.core_layer(task, case))

    def test_listener_requires_refused_not_timeout_permission_or_open(self):
        with patch.object(review.socket, "socket") as factory:
            probe = factory.return_value.__enter__.return_value
            for error in (review.errno.ECONNREFUSED, 10061):
                probe.connect_ex.return_value = error
                review.listener_closed(54321)
                probe.settimeout.assert_called_with(3)
            for error in (0, review.errno.EACCES, review.errno.ETIMEDOUT, 10013, 10060):
                probe.connect_ex.return_value = error
                with self.subTest(error=error), self.assertRaises(RuntimeError):
                    review.listener_closed(54321)

    def test_exact_inventory_distinguishes_db_and_full_core_fault(self):
        # Fixture có common file riêng: CMD03 không được nhận file này, còn
        # CMD05 SEC10/12 phải giữ nó theo snapshot thật, không cắt inventory.
        base_names = ["backend/pom.xml", "backend/services/identity-service/pom.xml",
                      "scripts/services.ps1", "scripts/db_lab.py", "ops/db-lab/layout.json",
                      "scripts/tests/session_browser.mjs", "ops/identity/config.fixture",
                      "contracts/http/identity/identity.fixture",
                      "backend/services/identity-service/src/main/fixture.java"]
        common = "contracts/http/common/common.fixture"
        with tempfile.TemporaryDirectory(prefix="r04d-inventory-") as directory:
            root = Path(directory)
            for name in base_names + [common]:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("non-secret fixture", encoding="utf-8")
            baseline = {str(Path(name)): "fixture" for name in base_names}
            fault = baseline | {str(Path(common)): "fixture"}
            with patch.object(review, "ROOT", root):
                review.require_core_inventory(baseline, "db", None)
                for case in ("SEC-10", "SEC-12"):
                    review.require_core_inventory(fault, "fault", case)
                    with self.assertRaises(RuntimeError):
                        review.require_core_inventory(baseline, "fault", case)
                with self.assertRaises(RuntimeError):
                    review.require_core_inventory(fault, "db", None)
                for task, case, inputs in (("db", None, baseline), ("fault", "SEC-10", fault)):
                    for changed in (dict(list(inputs.items())[1:]), inputs | {"unrelated.fixture": "fixture"}):
                        with self.subTest(task=task), self.assertRaises(RuntimeError):
                            review.require_core_inventory(changed, task, case)

    def test_inventory_rejects_unimplemented_layer(self):
        with self.assertRaises(RuntimeError):
            review.require_core_inventory({}, "fault", "SEC-11")

    def suite(self, xml: str):
        # Fixture nằm trong temp riêng, không sửa XML/evidence actual đã giữ.
        with tempfile.TemporaryDirectory(prefix="r04d-report-gate-") as directory:
            root = Path(directory)
            (root / "TEST-fixture.xml").write_text(xml, encoding="utf-8")
            return review.suites(root)

    def test_complete_suite_is_accepted(self):
        data = self.suite('<testsuite name="fixture" tests="1" failures="0" errors="0" skipped="0"><testcase name="real-case"/></testsuite>')
        self.assertEqual(data["fixture"]["cases"], ["real-case"])

    def test_missing_suite_is_rejected(self):
        with tempfile.TemporaryDirectory(prefix="r04d-report-gate-") as directory:
            with self.assertRaises(RuntimeError):
                review.suites(Path(directory))

    def test_zero_or_mismatched_summary_is_rejected(self):
        for count in (0, 2):
            with self.subTest(count=count), self.assertRaises(RuntimeError):
                self.suite(f'<testsuite name="fixture" tests="{count}" failures="0" errors="0" skipped="0"><testcase name="case"/></testsuite>')

    def test_summary_and_hidden_case_failures_are_rejected(self):
        # Summary0 không được che <failure/error/skipped> bên trong testcase.
        for tag, field in (("failure", "failures"), ("error", "errors"), ("skipped", "skipped")):
            for hidden in (False, True):
                with self.subTest(tag=tag, hidden=hidden), self.assertRaises(RuntimeError):
                    counts = {"failures": 0, "errors": 0, "skipped": 0}
                    counts[field] = 0 if hidden else 1
                    attrs = " ".join(f'{key}="{value}"' for key, value in counts.items())
                    self.suite(f'<testsuite name="fixture" tests="1" {attrs}><testcase name="case"><{tag}/></testcase></testsuite>')

    def test_duplicate_suite_names_are_rejected(self):
        with tempfile.TemporaryDirectory(prefix="r04d-report-gate-") as directory:
            root = Path(directory)
            xml = '<testsuite name="fixture" tests="1" failures="0" errors="0" skipped="0"><testcase name="case"/></testsuite>'
            for name in ("TEST-one.xml", "TEST-two.xml"):
                (root / name).write_text(xml, encoding="utf-8")
            with self.assertRaises(RuntimeError):
                review.suites(root)

    def test_gate_is_not_a_python_assert(self):
        # Cùng test chạy được bằng -O để chứng minh gate vẫn bật khi optimize.
        with self.assertRaises(RuntimeError):
            review.check(False, "expected deny")


if __name__ == "__main__":
    unittest.main()
