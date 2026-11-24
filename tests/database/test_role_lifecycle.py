import sys, tempfile, unittest
from pathlib import Path
from unittest.mock import patch, MagicMock
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'scripts/database'))
import migration_lifecycle as m
import role_lifecycle as r

def obj(name,kind='table',**kw):
    return dict(dict(kind=kind,name=name,args=None,owner='sakhtyar',rls=False,force_rls=False,subtype='r',security_definer=False,extension=False,public_execute=False,immutable_hint=False),**kw)
class RoleTests(unittest.TestCase):
    def setUp(self):
        self.o=[obj('flyway_schema_history'),obj('contract_revision',immutable_hint=True)]
        self.p=r.draft(self.o,[68]);self.p['reviewed']=True;self.p['objects']['table:contract_revision']={'decision':'ALLOW','privileges':['SELECT','INSERT'],'reason':'ledger'}
    def test_reviewed(self): self.assertEqual(len(r.validate(self.p,self.o,{})),2)
    def test_draft_block(self): self.p['reviewed']=False;self.assertRaises(m.GateError,r.validate,self.p,self.o,{})
    def test_unreviewed(self): self.p['objects']['table:contract_revision']['decision']='REVIEW';self.assertRaises(m.GateError,r.validate,self.p,self.o,{})
    def test_missing(self): self.assertRaises(m.GateError,r.validate,self.p,self.o+[obj('new')],{})
    def test_append_only(self): self.p['objects']['table:contract_revision']['privileges'].append('DELETE');self.assertRaises(m.GateError,r.validate,self.p,self.o,{})
    def test_history(self): self.p['objects']['table:flyway_schema_history']['privileges'].append('UPDATE');self.assertRaises(m.GateError,r.validate,self.p,self.o,{})
    def test_truncate(self): self.p['objects']['table:contract_revision']['privileges'].append('TRUNCATE');self.assertRaises(m.GateError,r.validate,self.p,self.o,{})
    def test_future(self):
        self.p['objects']['table:new']={'decision':'ALLOW','privileges':['SELECT'],'reason':'new'};self.p['pending_objects']=['table:new'];r.validate(self.p,self.o,{'new':{'privileges':['SELECT']}},allow_future=True);self.assertRaises(m.GateError,r.validate,self.p,self.o,{})
    def test_existing_future_rejected(self): self.p['pending_objects']=['table:contract_revision'];self.assertRaises(m.GateError,r.validate,self.p,self.o,{'contract_revision':{'privileges':['SELECT']}},allow_future=True)
    def test_only_target(self):
        sql='\n'.join(r.direct_sql(self.o,self.p));self.assertNotIn('ALL TABLES',sql);self.assertNotIn('TO PUBLIC',sql);self.assertNotIn('GRANT ALL',sql);self.assertIn('TO "sakhtyar_runtime"',sql)
    def test_role_safety(self):
        good=dict(login=True,super=False,bypass=False,create_role=False,create_db=False,replication=False,memberships=False,owns=False);self.assertTrue(r.safe_role(good));self.assertFalse(r.safe_role(dict(good,memberships=True)))
    def test_security_definer(self):
        o=obj('danger','routine',args='',security_definer=True,public_execute=True);self.p['objects'][r.key(o)]={'decision':'DENY','privileges':[],'reason':'unsafe'};self.assertRaises(m.GateError,r.validate,self.p,self.o+[o],{})
    def test_schema_diff(self):
        with tempfile.TemporaryDirectory() as d:
            result=m.schema_diagnostics(Path(d),b'CREATE TABLE x(a int);\n',b'CREATE TABLE x(a text);\n');self.assertNotEqual(result['source_sha256'],result['clone_sha256']);self.assertIn(b'CREATE TABLE', (Path(d)/result['private_diff']).read_bytes())
    def test_outer_marker(self):
        a=b'-- dump\n\\restrict abc\nSELECT 1;\n\\unrestrict abc\n';b=a.replace(b'abc',b'def');self.assertEqual(m.normalized_dump_sha(a),m.normalized_dump_sha(b));self.assertNotEqual(m.normalized_dump_sha(a),m.normalized_dump_sha(b.replace(b'SELECT 1',b'SELECT 2')))
    def test_marker_mismatch_not_ignored(self): self.assertIn(b'\\restrict',m.normalized_dump(b'\\restrict a\nSELECT 1;\n\\unrestrict b'))
    def test_no_role_apply_without_approval(self):
        # The dedicated role gate must reject privileges outside the reviewed policy before SQL.
        self.p['objects']['table:contract_revision']['privileges']=['INSERT','DELETE'];self.assertRaises(m.GateError,r.validate,self.p,self.o,{})
    def test_scram_verifier(self):
        import base64, hashlib, hmac
        password='A-long-unique-password-12345!'
        verifier=r.password_verifier(password)
        scheme,params,keys=verifier.split('$'); rounds,salt=params.split(':'); stored,server=keys.split(':')
        self.assertEqual('SCRAM-SHA-256',scheme)
        salted=hashlib.pbkdf2_hmac('sha256',password.encode(),base64.b64decode(salt),int(rounds))
        self.assertEqual(base64.b64decode(stored),hashlib.sha256(hmac.digest(salted,b'Client Key','sha256')).digest())
        self.assertEqual(base64.b64decode(server),hmac.digest(salted,b'Server Key','sha256'))
        self.assertNotIn(password,verifier)
    def test_scram_password_validation(self):
        for password in ('short','x'*24+'\n','x'*24+'é','x'*24+' '):
            self.assertRaises(m.GateError,r.password_verifier,password)
    def test_public_routine_draft_is_unreviewed(self):
        o=obj('utility','routine',args='',public_execute=True)
        rule=r.draft([o],[])['objects'][r.key(o)]
        self.assertEqual('REVIEW',rule['decision']);self.assertEqual(['EXECUTE'],rule['privileges'])
    def test_effective_inventory_missing_blocks(self):
        with patch.object(r,'safe_role',return_value=True),patch.object(r,'role_state'),patch.object(m,'restricted',return_value=True),patch.object(m,'role_info'),patch.object(m,'query',side_effect=[False,{}]):
            self.assertRaisesRegex(m.GateError,'inventory incomplete',r.verify,'clone','admin',self.o,self.p)
if __name__=='__main__': unittest.main()
