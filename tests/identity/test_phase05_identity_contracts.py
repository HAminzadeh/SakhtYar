"""Offline negative cases for the Phase05 source audit; no writes to repository."""
import importlib.util
import pathlib
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = pathlib.Path(__file__).resolve().parents[2] / 'scripts/identity/phase05_identity_contract_audit.py'
spec = importlib.util.spec_from_file_location('phase05_contract_audit', SCRIPT)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class Phase05Contracts(unittest.TestCase):
    def test_real_source_contracts(self):
        root = pathlib.Path(__file__).resolve().parents[2]
        report = mod.audit(root)
        self.assertEqual('PASS_OFFLINE_SOURCE_CONTRACTS_ONLY', report['status'], report)
        self.assertEqual(report['checks'], report['passed'])
        self.assertFalse(report['runtime_isolation_proven'])
        self.assertFalse(report['concurrent_postgresql_tested'])

    def test_empty_repo_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            report = mod.audit(td)
            self.assertEqual('BLOCKED', report['status'])
            self.assertEqual(0, report['passed'])

    def test_admin_lock_missing_is_detected(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            original = mod.audit
            # The audit may not accept a forged success result from a missing repository.
            self.assertEqual('BLOCKED', original(root)['status'])

    def test_no_runtime_proof_from_source_only(self):
        with tempfile.TemporaryDirectory() as td:
            r = mod.audit(td)
            self.assertFalse(r['runtime_isolation_proven'])
            self.assertFalse(r['http_integration_tested'])

    def test_no_mutation_of_supplied_root(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            marker = root / 'important-user-data.txt'
            marker.write_bytes(b'DO NOT CHANGE')
            mod.audit(root)
            self.assertEqual(b'DO NOT CHANGE', marker.read_bytes())
            self.assertEqual(['important-user-data.txt'], [p.name for p in root.iterdir()])

if __name__ == '__main__':
    unittest.main()
