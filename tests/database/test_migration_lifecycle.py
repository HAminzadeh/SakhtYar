import importlib.util, json, tempfile, unittest
from pathlib import Path
from unittest.mock import patch, MagicMock
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'scripts'/'database'))
import migration_lifecycle as m

class LifecycleTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.folder=Path(self.tmp.name)
        for n in (1,2,3): (self.folder/f'V{n}__test.sql').write_text('SELECT 1;',encoding='utf-8')
        self.entries=m.inventory(self.folder)
    def tearDown(self): self.tmp.cleanup()
    def rows(self,n):
        return [{'version':str(i),'script':f'V{i}__test.sql','type':'SQL','success':True,'checksum':i,'installed_by':'owner'} for i in range(1,n+1)]
    def test_pending(self): self.assertEqual([3],[e['version'] for e in m.history_gate(self.rows(2),self.entries)])
    def test_complete(self): self.assertEqual([],m.history_gate(self.rows(3),self.entries))
    def test_gap(self):
        (self.folder/'V2__test.sql').unlink(); self.assertRaises(m.GateError,m.inventory,self.folder)
    def test_duplicate(self):
        (self.folder/'V2__other.sql').write_text('SELECT 1;'); self.assertRaises(m.GateError,m.inventory,self.folder)
    def test_bad_name(self):
        (self.folder/'R__repeat.sql').write_text('SELECT 1;'); self.assertRaises(m.GateError,m.inventory,self.folder)
    def test_failed(self):
        rows=self.rows(2); rows[1]['success']=False; self.assertRaises(m.GateError,m.history_gate,rows,self.entries)
    def test_ahead(self): self.assertRaises(m.GateError,m.history_gate,self.rows(4),self.entries)
    def test_missing_checksum(self):
        rows=self.rows(1); rows[0]['checksum']=None; self.assertRaises(m.GateError,m.history_gate,rows,self.entries)
    def test_history_script_mismatch(self):
        rows=self.rows(1); rows[0]['script']='V1__other.sql'; self.assertRaises(m.GateError,m.history_gate,rows,self.entries)
    def test_order(self): self.assertRaises(m.GateError,m.history_gate,self.rows(2)[::-1],self.entries)
    def test_ident_reject(self): self.assertRaises(m.GateError,m.ident,'name;DROP')
    def test_restricted(self):
        good={x:True for x in ('login','schema_usage','db_connect','history_read')}
        good.update({x:False for x in ('superuser','bypassrls','createrole','createdb','replication','elevated_membership','owns_public_tables','owns_database','owns_public_schema','schema_create','history_write')})
        self.assertTrue(m.restricted(good)); self.assertFalse(m.restricted(dict(good,bypassrls=True))); self.assertFalse(m.restricted(dict(good,history_write=True)))
    def test_unreviewed_future_policy_blocks(self):
        op=object.__new__(m.Operator); op.policy={'68':{'tables':{}}}; self.assertRaises(m.GateError,op.requirements,[69]); self.assertEqual({},op.requirements([68]))
    def test_approval_blocks_before_backup(self):
        op=object.__new__(m.Operator); op.a=MagicMock(mode='ApplyDatabase',approve='wrong'); op.entries=self.entries; op.head='abcdef123456789'; op.pending=self.entries[-1:]
        for attr in ('source','connect','audit','image','fly','gate','backup','clone_preflight'): setattr(op,attr,MagicMock())
        op.c='x'; op.password='not-real'
        self.assertRaises(m.GateError,op.execute); op.backup.assert_not_called(); op.clone_preflight.assert_not_called()
    def test_dryrun_never_live_migrate(self):
        op=object.__new__(m.Operator); op.a=MagicMock(mode='DryRun'); op.entries=self.entries; op.head='abcdef123456789'; op.pending=self.entries[-1:]
        for attr in ('source','connect','audit','image','fly','gate','backup','clone_preflight'): setattr(op,attr,MagicMock())
        op.c='x'; op.password='not-real'; op.execute(); op.fly.assert_called_once_with('x','not-real','validate'); op.clone_preflight.assert_called_once_with(True)
    def test_audit_no_flyway_or_backup(self):
        op=object.__new__(m.Operator); op.a=MagicMock(mode='Audit')
        for attr in ('source','connect','audit','image','fly','gate','backup','clone_preflight'): setattr(op,attr,MagicMock())
        op.execute(); op.fly.assert_not_called(); op.backup.assert_not_called(); op.image.assert_not_called()
    def test_run_binary_stream(self):
        target=self.folder/'binary.out'; src=self.folder/'binary.in'; src.write_bytes(bytes(range(256)))
        m.run([sys.executable,'-c','import sys;sys.stdout.buffer.write(sys.stdin.buffer.read())'],input_file=src,target=target)
        self.assertEqual(src.read_bytes(),target.read_bytes())

if __name__=='__main__': unittest.main()
