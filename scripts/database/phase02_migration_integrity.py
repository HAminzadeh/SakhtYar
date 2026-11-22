#!/usr/bin/env python3
"""P02: source-only Flyway integrity and migration inventory. No database access.

Uses Git blob IDs of V1..V66 from inspected GitHub v2; these are NOT Flyway CRC32.
Never logs SQL text, credentials, connection strings or business data.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

MIGRATION = re.compile(r"V([1-9][0-9]*)__([a-zA-Z0-9_]+)\.sql\Z")
BLOB = re.compile(r"[0-9a-f]{40}\Z")
BASELINE = "da95d441c0a2325e0ea0cbad1d5b1540123277cd"


class MigrationCheckError(Exception):
    pass


def load_manifest(path: Path) -> dict[int, tuple[str, str]]:
    entries: dict[int, tuple[str, str]] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        if len(parts) != 2:
            raise MigrationCheckError("Malformed manifest line")
        filename, sha = parts
        m = MIGRATION.fullmatch(filename)
        if m is None or BLOB.fullmatch(sha) is None:
            raise MigrationCheckError("Invalid version or Git blob in manifest")
        n = int(m[1])
        if n in entries:
            raise MigrationCheckError(f"Duplicate baseline migration V{n}")
        entries[n] = (filename, sha)
    if sorted(entries) != list(range(1, 67)):
        raise MigrationCheckError("Baseline must have exactly V1..V66")
    return entries


def inventory(folder: Path) -> dict[int, Path]:
    found: dict[int, Path] = {}
    if not folder.is_dir():
        raise MigrationCheckError("Migration directory missing")
    for p in folder.iterdir():
        if not p.is_file() or not p.name.startswith("V") or p.suffix.lower() != ".sql":
            continue
        m = MIGRATION.fullmatch(p.name)
        if m is None:
            raise MigrationCheckError("Unexpected Flyway filename: " + p.name)
        n = int(m[1])
        if n in found:
            raise MigrationCheckError("Duplicate Flyway version: V" + str(n))
        if not p.read_bytes().strip():
            raise MigrationCheckError("Empty migration: V" + str(n))
        found[n] = p
    if not found or sorted(found) != list(range(1, max(found) + 1)):
        raise MigrationCheckError("Flyway versions have gaps")
    return found


def git(repo: Path, *args: str) -> str:
    p = subprocess.run(["git", *args], cwd=repo, stdout=subprocess.PIPE,
                       stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    if p.returncode != 0:
        raise MigrationCheckError("git metadata inspection failed: " + args[0])
    return p.stdout.strip()


def audit(repo: Path, manifest_file: Path) -> dict:
    expected = load_manifest(manifest_file)
    folder = repo / "backend/app/src/main/resources/db/migration"
    found = inventory(folder)
    if any(k not in found for k in expected):
        raise MigrationCheckError("Historical Flyway version missing")
    for n, (filename, blob) in expected.items():
        if found[n].name != filename:
            raise MigrationCheckError(f"Historical Flyway filename changed V{n}")
        rel = "backend/app/src/main/resources/db/migration/" + filename
        committed = git(repo, "rev-parse", "HEAD:" + rel)
        if committed.lower() != blob:
            raise MigrationCheckError(f"Historical committed migration drift: V{n}")
        # Check both staged and unstaged edits without printing SQL contents.
        dirty = subprocess.run(["git", "diff", "--quiet", "HEAD", "--", rel], cwd=repo)
        if dirty.returncode != 0:
            raise MigrationCheckError(f"Historical migration has local changes: V{n}")
    for n in (58, 60, 61, 65, 66):
        if n not in found:
            raise MigrationCheckError(f"Required scope migration missing V{n}")
    structural = {58: "data_scope", 60: "FORCE ROW LEVEL SECURITY",
                  61: "trg_sakhtyar_test_egress", 65: "simulation_fixture_contract_ledger",
                  66: "chk_sim_fixture_ledger_test_only"}
    for n, needle in structural.items():
        if needle not in found[n].read_text(encoding="utf-8-sig"):
            raise MigrationCheckError(f"Expected shared-DB contract marker missing: V{n}")
    return {
        "status": "PASS_SOURCE_ONLY_NOT_DB_PROOF",
        "baseline": BASELINE,
        "historical_migrations_untouched": len(expected),
        "highest_version_present": max(found),
        "additional_versions_after_baseline": [n for n in sorted(found) if n > 66],
        "source_scope_contract_markers": "PRESENT",
        "database_connection_opened": False,
        "flyway_validate_executed": False,
        "global_rls_verified": False,
        "business_e2e_writes_authorized": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, default=Path(__file__).with_name("phase02_v1_v66_git_blobs.txt"))
    args = parser.parse_args()
    try:
        print(json.dumps(audit(args.root.resolve(), args.manifest), ensure_ascii=False, indent=2))
        return 0
    except (MigrationCheckError, OSError, UnicodeError) as exc:
        print(json.dumps({"status": "BLOCKED", "reason": str(exc),
                          "database_changes": False}, ensure_ascii=False))
        return 2


if __name__ == "__main__":
    sys.exit(main())
