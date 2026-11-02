#!/usr/bin/env python3
"""Fail-closed source/artifact/privacy review for BL-R07."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import zipfile


ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend/services/content-service"
PRIVATE_SENTINELS = (
    b"PRIVATE-SENTINEL",
    b"secret-sentinel",
    b"expectedSentinel",
)


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    jar = args.jar.resolve()
    check(jar.is_file(), "Current packaged Content JAR is missing")

    public_contract = ROOT / "contracts/http/content/public"
    schemas = sorted(public_contract.glob("*.schema.json"))
    check(len(schemas) == 3, "Expected exactly three public response schemas")
    for schema in schemas:
        parsed = json.loads(schema.read_text(encoding="utf-8"))
        check(parsed.get("additionalProperties") is False, f"Root schema must be closed: {schema.name}")

    migration_hashes = {}
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        check(
            "BOOT-INF/classes/lab/backend/content/browser/BrowserHandler.class" in names,
            "Browser runtime classes are absent from JAR",
        )
        check(
            not any("content-v2/" in name or "contracts/fixtures/publication" in name for name in names),
            "Authoring or private publication fixture leaked into JAR",
        )
        check(
            not any("src/test" in name or "BrowserRuntimeIT" in name for name in names),
            "Test fixture leaked into JAR",
        )
        for migration in sorted((SERVICE / "src/main/resources/db/migration").glob("V[1-6]__*.sql")):
            entry = "BOOT-INF/classes/db/migration/" + migration.name
            check(entry in names, f"Packaged migration missing: {migration.name}")
            packaged = archive.read(entry)
            check(packaged == migration.read_bytes(), f"Packaged migration differs: {migration.name}")
            migration_hashes[migration.name] = hashlib.sha256(packaged).hexdigest()
        searchable = b"\n".join(
            archive.read(name)
            for name in names
            if name.startswith("BOOT-INF/classes/") and not name.endswith("/")
        )
        for sentinel in PRIVATE_SENTINELS:
            check(sentinel not in searchable, f"Private/credential sentinel leaked: {sentinel!r}")

    payload = {
        "schemaVersion": 1,
        "task": "R07",
        "status": "PASS",
        "jarSha256": sha(jar),
        "migrationSha256": migration_hashes,
        "contractSha256": {path.name: sha(path) for path in schemas},
        "checks": [
            "browser classes packaged",
            "V1-V6 packaged bytes match source",
            "no authoring/private/test fixture paths",
            "no private/credential sentinels in BOOT-INF/classes",
            "three closed public response schemas",
        ],
        "limits": [
            "Static review complements, and does not replace, PostgreSQL/TLS runtime evidence",
            "Canonical Identity end-to-end remains an affected R04d regression",
        ],
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(f"PASS R07 browser review: {args.report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
