"""P02 static-only tests: no DB, no outbound network, no credentials."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/database/phase02_migration_integrity.py"
spec = importlib.util.spec_from_file_location("phase02_migration_integrity", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class Phase02OfflineContractTest(unittest.TestCase):
    def test_baseline_is_exactly_66_contiguous_committed_blobs(self):
        rows = module.load_manifest(SCRIPT.with_name("phase02_v1_v66_git_blobs.txt"))
        self.assertEqual(list(rows), list(range(1, 67)))
        self.assertEqual(rows[60][0], "V60__core_test_scope_guard.sql")
        self.assertEqual(rows[66][0], "V66__simulation_fixture_ledger_test_scope.sql")

    def test_duplicate_versions_blocked(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)
            (p / "V1__old.sql").write_text("select 1;")
            (p / "V1__other.sql").write_text("select 2;")
            with self.assertRaisesRegex(module.MigrationCheckError, "Duplicate"):
                module.inventory(p)

    def test_missing_intermediate_version_blocked(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp)
            (p / "V1__first.sql").write_text("select 1;")
            (p / "V3__third.sql").write_text("select 3;")
            with self.assertRaisesRegex(module.MigrationCheckError, "gaps"):
                module.inventory(p)

    def test_malformed_manifest_blocked(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "manifest.txt"
            p.write_text("V1__first.sql invalid-blob\n")
            with self.assertRaises(module.MigrationCheckError):
                module.load_manifest(p)

    def test_audit_sql_is_read_only_transaction_and_has_no_business_select(self):
        for name in ("phase02_catalog_readonly.sql", "phase02_isolation_gate_readonly.sql"):
            sql = (ROOT / "scripts/database" / name).read_text(encoding="utf-8")
            self.assertIn("BEGIN TRANSACTION READ ONLY", sql)
            self.assertIn("ROLLBACK;", sql)
            # DDL/DML statements never appear at a line beginning (comments excluded).
            commands = [s.strip() for s in sql.splitlines() if s.strip() and not s.strip().startswith("--")]
            self.assertFalse(any(s.upper().startswith(("INSERT ", "UPDATE ", "DELETE ",
                                                        "ALTER ", "DROP ", "CREATE ", "TRUNCATE "))
                                 for s in commands))


if __name__ == "__main__":
    unittest.main(verbosity=2)
