import os
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'scripts' / 'database'))
import migration_lifecycle as m


class AutomaticStartupLifecycleTests(unittest.TestCase):
    def make_operator(self, pending):
        op = object.__new__(m.Operator)
        op.a = SimpleNamespace(mode='Startup', check_backend=False)
        op.pending = pending
        op.role = 'sakhtyar_runtime'
        op.c = 'local-postgres-container'
        op.owner = 'sakhtyar_owner'
        op.password = 'not-a-real-password'
        op.source = MagicMock()
        op.connect = MagicMock()
        op.audit = MagicMock()
        op.image = MagicMock()
        op.fly = MagicMock()
        op.gate = MagicMock()
        op.verify_current = MagicMock()
        op.quiet = MagicMock()
        op.prepare_role_policy = MagicMock()
        op.backup = MagicMock()
        op.clone_preflight = MagicMock()
        return op

    def test_startup_requires_local_profile_before_source_access(self):
        op = self.make_operator([])
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(m.GateError):
                op.execute()
        op.source.assert_not_called()

    def test_current_database_is_validated_without_backup_or_clone(self):
        op = self.make_operator([])
        with patch.dict(os.environ, {'SAKHTYAR_STARTUP_MIGRATION_PROFILE': 'local'}):
            op.execute()
        op.image.assert_called_once_with()
        op.fly.assert_called_once_with(op.c, op.password, 'validate')
        op.verify_current.assert_called_once_with()
        op.backup.assert_not_called()
        op.clone_preflight.assert_not_called()

    def test_pending_migrations_require_backup_and_clone_before_live_apply(self):
        op = self.make_operator([{'version': 74, 'name': 'V74__next.sql'}])
        op.clone_preflight.side_effect = m.GateError('test stop after clone preflight')
        with patch.dict(os.environ, {'SAKHTYAR_STARTUP_MIGRATION_PROFILE': 'local'}), patch.object(m, 'quiet'):
            with self.assertRaisesRegex(m.GateError, 'test stop after clone preflight'):
                op.execute()
        op.prepare_role_policy.assert_called_once_with()
        op.backup.assert_called_once_with()
        op.clone_preflight.assert_called_once_with(True)

    def test_startup_never_accepts_an_unrestricted_runtime_role(self):
        op = self.make_operator([{'version': 74, 'name': 'V74__next.sql'}])
        op.role = 'sakhtyar'
        with patch.dict(os.environ, {'SAKHTYAR_STARTUP_MIGRATION_PROFILE': 'local'}):
            with self.assertRaisesRegex(m.GateError, 'dedicated sakhtyar_runtime'):
                op.execute()
        op.backup.assert_not_called()
        op.clone_preflight.assert_not_called()


if __name__ == '__main__':
    unittest.main()
