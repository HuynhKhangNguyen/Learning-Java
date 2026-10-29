"""Fixtures kiểm auditor fail-closed; không chạy app/DB hoặc tạo runtime evidence."""
from __future__ import annotations

import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
import zipfile

import r04d_runtime_review as review


class RuntimeReviewTests(unittest.TestCase):
    def setUp(self):
        # Mỗi required variant có tên riêng; duplicate không thay rollback hoặc
        # purpose còn thiếu. Inventory tăng thì fixture dựng cùng shape thật.
        self.names = []
        for method, count in review.RUNTIME_CASES.items():
            self.names += ([method] if count == 1 else
                           [f"{method}(String)[{i}]" for i in range(1, count + 1)])

    def test_exact_twenty_one_cases(self):
        self.assertEqual(len(self.names), 21)
        review.runtime_cases(self.names)

    def test_each_selector_requires_exact_variants_not_full_baseline(self):
        # Một named selector không được PASS từ XML baseline cũ hoặc thiếu
        # một variant; native command selection được helper kiểm riêng.
        for selector, methods in review.RUNTIME_SELECTORS.items():
            selected = [name for name in self.names if name.split("(")[0] in methods]
            with self.subTest(selector=selector):
                review.runtime_cases(selected, selector)
                for missing in selected:
                    with self.assertRaises(RuntimeError):
                        review.runtime_cases([name for name in selected if name != missing], selector)
                with self.assertRaises(RuntimeError):
                    review.runtime_cases(selected + ["unexpectedMethod"], selector)
                with self.assertRaises(RuntimeError):
                    review.runtime_cases(self.names, selector)
        with self.assertRaises(RuntimeError):
            review.runtime_cases(self.names, "DATA-16")

    def test_timeout_permission_and_live_socket_are_not_closed_evidence(self):
        # Socket inaccessible trong sandbox không là closed-port PASS.
        with patch.object(review.socket, "socket") as factory:
            probe = factory.return_value.__enter__.return_value
            for error in (review.errno.ECONNREFUSED, 10061):
                probe.connect_ex.return_value = error
                review.listener_closed(17441)
                probe.settimeout.assert_called_with(3)
            for error in (0, review.errno.EACCES, review.errno.ETIMEDOUT, 10013, 10060):
                probe.connect_ex.return_value = error
                with self.subTest(error=error), self.assertRaises(RuntimeError):
                    review.listener_closed(17441)

    def test_exact_redacted_browser_report(self):
        for purpose in ("LOGOUT_ALL", "PASSWORD_CHANGE"):
            report = {"stage": "RESULT", "status": "PASS", "purpose": purpose, "checks": len(review.BROWSER_CASES),
                      "cases": sorted(review.BROWSER_CASES), "browser": "installed Microsoft Edge / Chromium",
                      "version": "1.2.3.4", "origin": "https://localhost:17441", "scope": "fixture only"}
            review.browser_cases(report, purpose)
            # Mỗi label là một hành vi bắt buộc; count xanh, duplicate, purpose
            # khác hoặc secret field thừa không thể che thiếu evidence browser.
            for label in review.BROWSER_CASES:
                bad = dict(report, cases=[value for value in report["cases"] if value != label])
                with self.subTest(label=label), self.assertRaises(RuntimeError):
                    review.browser_cases(bad, purpose)
            for changed in ({"status": "FAIL"}, {"checks": True}, {"purpose": "LEARNING_RESET"},
                            {"origin": "https://wrong.example"}, {"cookie": "not-a-real-secret"},
                            {"cases": report["cases"][:-1] + [report["cases"][0]]}):
                with self.subTest(changed=changed), self.assertRaises(RuntimeError):
                    review.browser_cases(dict(report, **changed), purpose)

    def test_each_missing_case_is_rejected(self):
        for missing in self.names:
            with self.subTest(missing=missing), self.assertRaises(RuntimeError):
                review.runtime_cases([name for name in self.names if name != missing])

    def test_duplicate_variant_is_not_two_cases(self):
        with self.assertRaises(RuntimeError):
            review.runtime_cases(self.names[:-1] + [self.names[-2]])

    def test_similar_name_cannot_replace_required_case(self):
        for suffix in ("Changed", "[not-a-number]", "-extra"):
            with self.subTest(suffix=suffix), self.assertRaises(RuntimeError):
                review.runtime_cases([self.names[0] + suffix] + self.names[1:])

    def test_summary_and_nodes_both_required(self):
        with tempfile.TemporaryDirectory(prefix="r04d-runtime-report-") as disposable:
            directory = Path(disposable)
            file = directory / "TEST-canonical.xml"
            suite = ET.Element("testsuite", name=review.RUNTIME_SUITE, tests=str(len(self.names)), failures="0", errors="0", skipped="0")
            for name in self.names:
                ET.SubElement(suite, "testcase", name=name)
            ET.ElementTree(suite).write(file, encoding="utf-8")
            self.assertEqual(review.suites(directory)[review.RUNTIME_SUITE]["tests"], len(self.names))
            # Summary zero không được che node failure/error/skip thực tế.
            for tag in ("failure", "error", "skipped"):
                child = ET.SubElement(suite[0], tag)
                ET.ElementTree(suite).write(file, encoding="utf-8")
                with self.subTest(tag=tag), self.assertRaises(RuntimeError):
                    review.suites(directory)
                suite[0].remove(child)
            suite.set("tests", str(len(self.names)+1))
            ET.ElementTree(suite).write(file, encoding="utf-8")
            with self.assertRaises(RuntimeError):
                review.suites(directory)

    def test_summary_totals_cannot_invent_pass(self):
        reports = {"suite": {"tests": 6}}
        good = {"tests": 6, "failures": 0, "errors": 0, "skipped": 0}
        review.report_totals(reports, good)
        for key in good:
            bad = dict(good)
            bad[key] += 1
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                review.report_totals(reports, bad)

    def test_artifact_rejects_fixture_cross_service_and_stale_bytes(self):
        # ZIP tối thiểu chỉ kiểm gate packaging, không giả product JAR qualified.
        with tempfile.TemporaryDirectory(prefix="r04d-runtime-artifact-") as disposable, patch.object(review, "ROOT", Path(disposable)):
            root = Path(disposable)
            service = root / "backend/services/content-service"
            compiled = service / "target/classes/lab/backend/content/transport/TransportSettings.class"
            migration = service / "src/main/resources/db/migration/V1.sql"
            compiled.parent.mkdir(parents=True)
            migration.parent.mkdir(parents=True)
            compiled.write_bytes(b"fixture-current-class")
            migration.write_bytes(b"fixture-migration")
            evidence = root / "evidence"
            retained = evidence / "content/qualified-artifact.jar"
            retained.parent.mkdir(parents=True)
            current = service / "target/content-service-0.1.0-SNAPSHOT.jar"
            entries = {"BOOT-INF/classes/lab/backend/content/transport/TransportSettings.class": compiled.read_bytes(),
                       "BOOT-INF/classes/db/migration/V1.sql": migration.read_bytes()}
            for bad in (None, "BOOT-INF/classes/fixture.p12", "BOOT-INF/lib/junit-jupiter-api-1.jar",
                        "BOOT-INF/lib/identity-service-0.1.0-SNAPSHOT.jar",
                        "BOOT-INF/classes/lab/backend/content/identityaccess/CanonicalAccessFixtureMain.class",
                        "BOOT-INF/classes/lab/backend/content/identityaccess/CanonicalFenceFaultPeer.class",
                        "BOOT-INF/classes/lab/backend/identity/session/SessionAuthority.class", "stale-class", "stale-migration"):
                candidate = dict(entries)
                if bad == "stale-class":
                    candidate[next(iter(entries))] = b"fixture-old-class"
                elif bad == "stale-migration":
                    candidate["BOOT-INF/classes/db/migration/V1.sql"] = b"fixture-old-migration"
                elif bad:
                    candidate[bad] = b"fixture-forbidden-entry"
                with zipfile.ZipFile(retained, "w") as archive:
                    for name, value in candidate.items():
                        archive.writestr(name, value)
                current.write_bytes(retained.read_bytes())
                summary = {"artifactSha256": review.digest(retained)}
                if bad is None:
                    self.assertEqual(review.artifact("content", evidence, summary), summary["artifactSha256"])
                else:
                    with self.subTest(entry=bad), self.assertRaises((RuntimeError, KeyError)):
                        review.artifact("content", evidence, summary)


if __name__ == "__main__":
    unittest.main()
