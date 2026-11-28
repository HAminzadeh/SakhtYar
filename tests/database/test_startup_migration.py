import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'scripts' / 'database'))
import startup_migration as startup


class StartupAdapterTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / 'scripts/database').mkdir(parents=True)
        (self.root / 'scripts/database/migration_lifecycle.py').write_text('# lifecycle', encoding='utf-8')
        (self.root / 'scripts/database/role_lifecycle.py').write_text('# role lifecycle', encoding='utf-8')
        self.git = Mock(return_value='v2')
        self.runner = Mock(return_value=0)

    def tearDown(self):
        self.tmp.cleanup()

    def test_missing_policy_generates_draft_and_blocks_live_migration(self):
        code = startup.execute(self.root, 'local', startup.ROLE, startup.ROLE, True,
                               python='python', runner=self.runner, git_reader=self.git)
        self.assertEqual(20, code)
        command = self.runner.call_args.args[0]
        self.assertIn('DraftClone', command)
        self.assertNotIn('Startup', command)

    def test_untracked_policy_is_not_overwritten(self):
        path = self.root / startup.POLICY
        path.write_text('{"reviewed": true}', encoding='utf-8')
        def git_reader(root, *args):
            if args[0] == 'branch': return 'v2'
            raise startup.StartupGateError('not tracked')
        with self.assertRaisesRegex(startup.StartupGateError, 'not tracked'):
            startup.execute(self.root, 'local', startup.ROLE, startup.ROLE, True,
                            runner=self.runner, git_reader=git_reader)
        self.runner.assert_not_called()
        self.assertTrue(path.exists())

    def test_unreviewed_policy_blocks(self):
        path = self.root / startup.POLICY
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({'reviewed': False}), encoding='utf-8')
        def git_reader(root, *args): return 'v2' if args[0] == 'branch' else 'scripts/database/runtime_privileges.reviewed.json'
        with self.assertRaisesRegex(startup.StartupGateError, 'still a draft'):
            startup.execute(self.root, 'local', startup.ROLE, startup.ROLE, True,
                            runner=self.runner, git_reader=git_reader)
        self.runner.assert_not_called()

    def test_wrong_runtime_login_blocks_before_migration(self):
        path = self.root / startup.POLICY
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({'reviewed': True}), encoding='utf-8')
        def git_reader(root, *args): return 'v2' if args[0] == 'branch' else 'scripts/database/runtime_privileges.reviewed.json'
        with self.assertRaisesRegex(startup.StartupGateError, 'must use SAKHTYAR_RUNTIME_DB_USER'):
            startup.execute(self.root, 'local', startup.ROLE, 'sakhtyar', True,
                            runner=self.runner, git_reader=git_reader)
        self.runner.assert_not_called()

    def test_missing_runtime_password_blocks_before_migration(self):
        path = self.root / startup.POLICY
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({'reviewed': True}), encoding='utf-8')
        def git_reader(root, *args): return 'v2' if args[0] == 'branch' else 'scripts/database/runtime_privileges.reviewed.json'
        with self.assertRaisesRegex(startup.StartupGateError, 'PASSWORD is required'):
            startup.execute(self.root, 'local', startup.ROLE, startup.ROLE, False,
                            runner=self.runner, git_reader=git_reader)
        self.runner.assert_not_called()

    def test_reviewed_policy_invokes_startup_lifecycle(self):
        path = self.root / startup.POLICY
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({'reviewed': True}), encoding='utf-8')
        def git_reader(root, *args): return 'v2' if args[0] == 'branch' else 'scripts/database/runtime_privileges.reviewed.json'
        code = startup.execute(self.root, 'local', startup.ROLE, startup.ROLE, True,
                               python='python', runner=self.runner, git_reader=git_reader)
        self.assertEqual(0, code)
        command = self.runner.call_args.args[0]
        self.assertIn('Startup', command)
        self.assertIn('--runtime-role', command)
        self.assertEqual('local', self.runner.call_args.args[2]['SAKHTYAR_STARTUP_MIGRATION_PROFILE'])

    def test_non_local_profile_is_rejected(self):
        with self.assertRaisesRegex(startup.StartupGateError, 'only for the local profile'):
            startup.execute(self.root, 'development', startup.ROLE, startup.ROLE, True,
                            runner=self.runner, git_reader=self.git)
        self.runner.assert_not_called()


if __name__ == '__main__':
    unittest.main()
