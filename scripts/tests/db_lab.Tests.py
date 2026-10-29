"""R02a path/process guards only; these are not PostgreSQL integration proof."""
from __future__ import annotations

import importlib.util
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("db_lab", ROOT / "scripts/db_lab.py")
lab = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lab)
sys.modules["db_lab"] = lab
trust_spec = importlib.util.spec_from_file_location("trust_lab", ROOT / "scripts/trust_lab.py")
trust = importlib.util.module_from_spec(trust_spec)
trust_spec.loader.exec_module(trust)


class LabGuards(unittest.TestCase):
    def setUp(self):
        lab.LAB_ROOT.mkdir(parents=True, exist_ok=True)
        self.directory = Path(tempfile.mkdtemp(prefix="run-guard-", dir=lab.LAB_ROOT)).resolve()
        self.run_id = str(uuid.uuid4())
        self.marker = {"schemaVersion": 1, "runId": self.run_id, "workspace": str(ROOT),
                       "dataDirectory": str(self.directory / "data")}
        self.write_marker()

    def write_marker(self):
        (self.directory / "ownership.json").write_text(json.dumps(self.marker), encoding="utf-8")

    def test_valid_owned_directory(self):
        lab.assert_owned(self.directory, self.run_id)

    def test_root_and_outside_refused(self):
        for path in [lab.LAB_ROOT, ROOT / ".tools"]:
            with self.subTest(path=path), self.assertRaisesRegex(RuntimeError, "outside"):
                lab.assert_owned(path, self.run_id)

    def test_wrong_run_workspace_or_data_refused(self):
        for key in ["runId", "workspace", "dataDirectory"]:
            original = self.marker[key]
            self.marker[key] = "wrong"
            self.write_marker()
            with self.subTest(field=key), self.assertRaisesRegex(RuntimeError, "marker mismatch"):
                lab.assert_owned(self.directory, self.run_id)
            self.marker[key] = original

    def test_linked_data_refused(self):
        target = self.directory / "other-data"
        target.mkdir()
        if os.name == "nt":
            # Native PowerShell creates a junction; no symlink privilege required.
            # Both paths stay in this ignored fixture, which is retained for review.
            command = "$ErrorActionPreference='Stop'; New-Item -ItemType Junction -Path $args[0] -Target $args[1] | Out-Null"
            script = self.directory / "junction.ps1"
            script.write_text(command, encoding="utf-8")
            subprocess.run(["pwsh", "-NoProfile", "-File", str(script), str(self.directory / "data"), str(target)],
                           check=True, creationflags=lab.FLAG, capture_output=True, timeout=15)
        else:
            (self.directory / "data").symlink_to(target, target_is_directory=True)
        with self.assertRaisesRegex(RuntimeError, "resolves through a link"):
            lab.assert_owned(self.directory, self.run_id)

    def test_missing_marker_refused(self):
        (self.directory / "ownership.json").unlink()
        with self.assertRaises(FileNotFoundError):
            lab.assert_owned(self.directory, self.run_id)

    def test_linked_lab_root_refused(self):
        # Reuse the real data-junction fixture to point a test lab root at a
        # different directory. No writes/provision/stop may follow that link.
        self.test_linked_data_refused()
        original = lab.LAB_ROOT
        try:
            lab.LAB_ROOT = self.directory / "data"
            with self.assertRaisesRegex(RuntimeError, "Lab root resolves through a link"):
                lab.assert_owned(self.directory, self.run_id)
        finally:
            lab.LAB_ROOT = original

    def test_timed_out_child_tree_stopped(self):
        # A child process owns a real listening socket. The parent waits on it.
        # Timeout must end both processes, then this listener must be closed.
        script = self.directory / "timeout-parent.py"
        ready = self.directory / "child-ready.json"
        child = "import socket,time,json,os; s=socket.socket(); s.bind(('127.0.0.1',0)); s.listen(); open(" + repr(str(ready)) + ",'w').write(json.dumps({'port':s.getsockname()[1],'pid':os.getpid()})); time.sleep(30)"
        script.write_text("import subprocess,sys; subprocess.run([sys.executable,'-c'," + repr(child) + "])", encoding="utf-8")
        with self.assertRaisesRegex(RuntimeError, "timed out"):
            lab.run([sys.executable, str(script)], log=self.directory / "timeout.log", timeout=3)
        self.assertTrue(ready.is_file(), "Child did not reach real listener before timeout")
        endpoint = json.loads(ready.read_text(encoding="utf-8"))
        with socket.socket() as probe:
            self.assertNotEqual(0, probe.connect_ex(("127.0.0.1", endpoint["port"])))

    def test_linked_trust_root_refused_before_pki_provisioning(self):
        fixture_tools = self.directory / ".tools"
        fixture_tools.mkdir()
        target = self.directory / "foreign-pki"
        target.mkdir()
        link = fixture_tools / "trust-lab"
        if os.name == "nt":
            script = self.directory / "trust-junction.ps1"
            script.write_text("$ErrorActionPreference='Stop'; New-Item -ItemType Junction -Path $args[0] -Target $args[1] | Out-Null", encoding="utf-8")
            subprocess.run(["pwsh", "-NoProfile", "-File", str(script), str(link), str(target)],
                           check=True, creationflags=lab.FLAG, capture_output=True, timeout=15)
        else:
            link.symlink_to(target, target_is_directory=True)
        original = trust.ROOT
        try:
            trust.ROOT = self.directory
            with self.assertRaisesRegex(RuntimeError, "Trust lab root resolves through a link"):
                trust.create_bundle(self.directory, {})
            self.assertEqual([], list(target.iterdir()), "Rejected PKI root must not receive files")
        finally:
            trust.ROOT = original


if __name__ == "__main__":
    unittest.main(verbosity=2)
