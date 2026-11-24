import sys, tempfile, unittest
from pathlib import Path
from unittest.mock import MagicMock, patch
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'scripts/database'))
import migration_lifecycle as m
import local_schema_upgrade as s

class LocalSchemaTests(unittest.TestCase):
    def setUp(self):
        self.entries=[{'version':i,'name':f'V{i}__x.sql','sha256':'x'} for i in range(1,69)]
        self.entries[-1].update(name=s.REVIEWED[68][0],sha256=s.REVIEWED[68][1])
    def test_exact_review(self): s.approved_inventory(self.entries,self.entries[-1:]); s.approved_inventory(self.entries,[],True)
    def test_future(self):
        with self.assertRaises(m.GateError): s.approved_inventory(self.entries+[{'version':69}],self.entries[-1:])
    def test_changed_sql(self):
        self.entries[-1]['sha256']='bad'
        with self.assertRaises(m.GateError): s.approved_inventory(self.entries,self.entries[-1:])
    def test_wrong_pending(self):
        for pending in ([],self.entries[-2:]):
            with self.assertRaises(m.GateError): s.approved_inventory(self.entries,pending)
    def test_token_drift(self):
        a=['head','container',[], 'schema','roles', self.entries]
        token=s.approval(*a)
        for i in (0,1,3,4):
            b=list(a);b[i]='changed';self.assertNotEqual(token,s.approval(*b))
        self.assertEqual(token,s.approval(*a))
    def test_structural_checks(self):
        req={t:{} for t in s.NEW_TABLES}; req['builder_invitation']={'rls':True,'triggers':['trigger_a'],'policies':['policy_a']}
        with patch.object(m,'query',return_value=True) as q:
            s.assert_structure('c','u',req)
            sql=' '.join(x.args[2] for x in q.call_args_list)
            for needle in ['relforcerowsecurity','trigger_a','policy_a','aclexplode','grantee=0','to_regprocedure']:
                self.assertIn(needle,sql)
        with patch.object(m,'query',return_value=False):
            with self.assertRaises(m.GateError): s.assert_structure('c','u',req)
    def op(self,mode,approve=None):
        op=object.__new__(s.LocalSchemaOperator); op.a=MagicMock(mode=mode,approve=approve);op.entries=self.entries;op.pending=self.entries[-1:];op.before=[];op.before_tables=[];op.head='head';op.c='c';op.owner=op.user='owner';op.role=None;op.password='test';op.report={};
        for a in ('check_source_dependency','source','connect','audit','image','fly','gate','backup','clone','unchanged'):setattr(op,a,MagicMock())
        op.snapshot=MagicMock(return_value=([],'schema','roles'));op.requirements=MagicMock(return_value={t:{} for t in s.NEW_TABLES})
        return op
    @patch.object(m,'quiet')
    @patch.object(m,'query',return_value=True)
    def test_plan_no_write(self,q,quiet):
        op=self.op('Plan');op.execute();op.fly.assert_called_once_with('c','test','validate');op.backup.assert_not_called();op.clone.assert_not_called()
    @patch.object(m,'quiet')
    @patch.object(m,'query',return_value=True)
    def test_bad_approval_no_write(self,q,quiet):
        op=self.op('Apply','wrong')
        with self.assertRaises(m.GateError):op.execute()
        op.backup.assert_not_called();op.clone.assert_not_called();op.fly.assert_called_once_with('c','test','validate')
    @patch.object(m,'query',return_value=False)
    def test_existing_runtime_blocks(self,q):
        op=self.op('Apply','wrong')
        with self.assertRaises(m.GateError):op.execute()
        op.backup.assert_not_called()
    @patch.object(m,'query',return_value=True)
    def test_alternate_runtime_blocks(self,q):
        op=self.op('Apply','wrong');op.role='other'
        with self.assertRaises(m.GateError):op.execute()
        op.backup.assert_not_called()
    @patch.object(m,'quiet')
    @patch.object(m,'query',return_value=True)
    def test_clone_failure_no_live_migration(self,q,quiet):
        op=self.op('Apply',s.approval('head','c',[],'schema','roles',self.entries));op.schema_before='schema';op.roles_before='roles';op.clone.side_effect=m.GateError('clone failure')
        with self.assertRaises(m.GateError):op.execute()
        op.fly.assert_called_once_with('c','test','validate');op.unchanged.assert_not_called()
    @patch.object(s,'assert_structure')
    def test_verify_readonly(self,structure):
        op=self.op('Verify');op.pending=[];op.execute();op.fly.assert_called_once_with('c','test','validate');op.backup.assert_not_called();structure.assert_called_once()

if __name__=='__main__': unittest.main()
