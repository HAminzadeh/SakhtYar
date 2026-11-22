import importlib.util
import pathlib
import shutil
import tempfile
import unittest

HERE=pathlib.Path(__file__).resolve()
ROOT=HERE.parents[2]
SPEC=importlib.util.spec_from_file_location('audit_phase06',ROOT/'scripts/audit/phase06_audit_contracts.py')
MODULE=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

class AuditContracts(unittest.TestCase):
    def test_real_patched_contracts(self):
        result=MODULE.audit(ROOT)
        self.assertEqual('PASS_OFFLINE_SOURCE_CONTRACTS_ONLY',result['status'],result['failed'])
        self.assertEqual(result['checks'],result['passed'])
    def test_empty_tree_fails_closed(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertEqual('BLOCKED',MODULE.audit(d)['status'])
    def test_missing_authorization_fails(self):
        with tempfile.TemporaryDirectory() as d:
            for rel in ('backend/modules/identity/src/main/java/com/sakhtyar/identity/config/SecurityConfig.java',):
                path=pathlib.Path(d)/rel
                path.parent.mkdir(parents=True,exist_ok=True)
                path.write_text('public class Fake {}',encoding='utf-8')
            result=MODULE.audit(d)
            self.assertIn('HTTP explicit audit permissions',result['failed'])
    def test_audit_never_claims_runtime_proof(self):
        result=MODULE.audit(ROOT)
        self.assertFalse(result['postgresql_immutability_proven'])
        self.assertFalse(result['real_test_rls_proven'])
        self.assertFalse(result['http_integration_proven'])
    def test_audit_never_writes_input_tree(self):
        with tempfile.TemporaryDirectory() as d:
            MODULE.audit(d)
            self.assertEqual([],list(pathlib.Path(d).iterdir()))

if __name__ == '__main__': unittest.main()
