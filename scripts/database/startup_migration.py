#!/usr/bin/env python3
"""Local-only Spring startup adapter for the reviewed SakhtYar migration lifecycle.

The adapter never runs Flyway directly. It delegates to migration_lifecycle.py,
which performs source/history checks, checksum validation, backup restoration,
an isolated PostgreSQL clone, runtime privilege checks, and guarded live apply.
A missing runtime ACL policy triggers a safe DraftClone and blocks startup for review.
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

ROLE = "sakhtyar_runtime"
POLICY = Path("scripts/database/runtime_privileges.reviewed.json")
MIGRATION = Path("scripts/database/migration_lifecycle.py")
ROLE_LIFECYCLE = Path("scripts/database/role_lifecycle.py")


class StartupGateError(RuntimeError):
    pass


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(root), *args],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if result.returncode != 0:
        raise StartupGateError("Git source verification failed; inspect the local repository privately.")
    return result.stdout.strip()


def run_child(command: list[str], root: Path, environment: dict[str, str]) -> int:
    result = subprocess.run(
        command,
        cwd=str(root),
        env=environment,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if result.stdout:
        print(result.stdout, end="" if result.stdout.endswith("\n") else "\n", flush=True)
    return result.returncode


def execute(
    root: Path,
    profile: str,
    runtime_role: str,
    app_runtime_user: str,
    app_runtime_password_present: bool,
    python: str = sys.executable,
    runner=run_child,
    git_reader=git,
) -> int:
    root = root.resolve()
    if profile != "local":
        raise StartupGateError("Automatic migration is allowed only for the local profile.")
    if runtime_role != ROLE:
        raise StartupGateError("Only the reviewed sakhtyar_runtime role is allowed for application startup.")
    if not (root / MIGRATION).is_file() or not (root / ROLE_LIFECYCLE).is_file():
        raise StartupGateError("Migration lifecycle source files are missing from the repository.")
    if git_reader(root, "branch", "--show-current") != "v2":
        raise StartupGateError("Automatic startup migration requires the v2 source branch.")

    policy_path = root / POLICY
    environment = dict(os.environ)
    environment["SAKHTYAR_STARTUP_MIGRATION_PROFILE"] = "local"

    if not policy_path.exists():
        print(
            "[StartupMigration] First-run setup: reviewed runtime privilege policy is missing.",
            flush=True,
        )
        print(
            "[StartupMigration] Generating a read-only runtime policy draft from the actual catalog "
            "(and an isolated clone when migrations are pending); the live database will not be migrated in this run.",
            flush=True,
        )
        command = [
            python,
            "-B",
            str(root / ROLE_LIFECYCLE),
            "--mode",
            "DraftClone",
            "--root",
            str(root),
        ]
        code = runner(command, root, environment)
        if code != 0:
            return code
        print(
            "[StartupMigration] BLOCKED: review the generated runtime_privileges.DRAFT.json, "
            "save the approved result as scripts/database/runtime_privileges.reviewed.json, "
            "commit it, provision/verify sakhtyar_runtime, then run IntelliJ again.",
            flush=True,
        )
        return 20

    try:
        tracked_policy = git_reader(root, "ls-files", "--error-unmatch", str(POLICY))
    except StartupGateError:
        tracked_policy = ""
    if not tracked_policy:
        raise StartupGateError(
            "runtime_privileges.reviewed.json exists but is not tracked by Git. "
            "Review and commit the policy before startup migration."
        )

    try:
        policy = json.loads(policy_path.read_text(encoding="utf-8-sig"))
    except (OSError, UnicodeError, json.JSONDecodeError):
        raise StartupGateError("Reviewed runtime privilege policy is unreadable or invalid JSON.") from None
    if policy.get("reviewed") is not True:
        raise StartupGateError(
            "Runtime privilege policy is still a draft. Every object must be reviewed before startup migration."
        )
    if app_runtime_user != ROLE:
        raise StartupGateError(
            "Local Spring datasource must use SAKHTYAR_RUNTIME_DB_USER=sakhtyar_runtime; "
            "the migration owner must never be the application login."
        )
    if not app_runtime_password_present:
        raise StartupGateError(
            "SAKHTYAR_RUNTIME_DB_PASSWORD is required before database promotion; "
            "configure it in the local secrets file, not in source control."
        )

    command = [
        python,
        "-B",
        str(root / MIGRATION),
        "--mode",
        "Startup",
        "--root",
        str(root),
        "--runtime-role",
        runtime_role,
    ]
    return runner(command, root, environment)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True)
    parser.add_argument("--profile", required=True)
    parser.add_argument("--runtime-role", default=ROLE)
    parser.add_argument("--app-runtime-user", default="")
    parser.add_argument("--app-runtime-password-present", choices=("true", "false"), default="false")
    args = parser.parse_args()
    try:
        return execute(
            Path(args.root),
            args.profile,
            args.runtime_role,
            args.app_runtime_user,
            args.app_runtime_password_present == "true",
        )
    except StartupGateError as exc:
        print("[StartupMigration] BLOCKED: " + str(exc), file=sys.stderr, flush=True)
        return 21


if __name__ == "__main__":
    raise SystemExit(main())
