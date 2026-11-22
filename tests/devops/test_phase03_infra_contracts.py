from __future__ import annotations
import importlib.util, tempfile, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def load(name,path):
    spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
m=load('infra',ROOT/'scripts/devops/phase03_infra_contracts.py')
p=load('deploy',ROOT/'scripts/devops/phase03_deploy_preflight.py')
class InfraContracts(unittest.TestCase):
    def test_repository_offline_invariants(self):
        self.assertEqual(m.check(ROOT)['status'],'PASS_OFFLINE_SOURCE_CONTRACTS_ONLY')
    def test_missing_file_fails_closed(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(m.ContractError):m.check(Path(d))
    def test_no_missing_environment_secrets(self):
        with self.assertRaises(p.PreflightFailure):p.validate({})
    def test_same_database_admin_and_runtime_denied(self):
        x={k:'a'*64 for k in p.FIELDS};x.update(POSTGRES_USER='pg_owner',SAKHTYAR_RUNTIME_DB_USER='pg_owner',SAKHTYAR_DOMAIN='sakhtyar.org')
        with self.assertRaisesRegex(p.PreflightFailure,'equals'):p.validate(x)
    def test_bad_env_duplicate_fails_closed(self):
        with tempfile.TemporaryDirectory() as d:
            f=Path(d)/'.env';f.write_text('A=one\nA=two\n')
            with self.assertRaisesRegex(p.PreflightFailure,'Duplicate'):p.parse_env(f)
    def test_placeholder_secret_never_accepted(self):
        x={k:'a'*64 for k in p.FIELDS};x.update(POSTGRES_USER='pg_owner',SAKHTYAR_RUNTIME_DB_USER='sakhtyar_runtime',SAKHTYAR_DOMAIN='sakhtyar.org',APP_JWT_SECRET='CHANGE_ME_PASSWORD')
        with self.assertRaisesRegex(p.PreflightFailure,'Placeholder'):p.validate(x)
if __name__=='__main__':unittest.main(verbosity=2)
