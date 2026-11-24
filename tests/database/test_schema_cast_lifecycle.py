import sys,tempfile,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'scripts/database'))
import migration_lifecycle as m
OLD=b"ANY ((ARRAY['REAL'::character varying, 'TEST'::character varying])::text[])"
NEW=b"ANY (ARRAY[('REAL'::character varying)::text, ('TEST'::character varying)::text])"
def ddl(expr=OLD):
    return b"-- Name: x; Type: TABLE; Schema: public; Owner: sakhtyar\nCREATE TABLE public.x (\n    scope character varying,\n    CONSTRAINT chk_scope CHECK (((scope)::text = "+expr+b"))\n);\n"
class SchemaCastTests(unittest.TestCase):
    def compare(self,a,b):
        with tempfile.TemporaryDirectory() as d: return m.compare_schema(Path(d),a,b)
    def test_equivalent(self): self.assertTrue(self.compare(ddl(),ddl(NEW))['matches'])
    def test_raw_diff_kept(self):
        with tempfile.TemporaryDirectory() as d:
            x=m.compare_schema(Path(d),ddl(),ddl(NEW)); self.assertFalse(x['raw_equal']); self.assertEqual(x['source_casts'],1); self.assertTrue((Path(d)/'schema-diff.private.txt').exists())
    def test_value_changed(self): self.assertFalse(self.compare(ddl(),ddl(NEW.replace(b'TEST',b'OTHER')))['matches'])
    def test_predicate_changed(self): self.assertFalse(self.compare(ddl(),ddl(NEW).replace(b' = ANY',b' <> ANY'))['matches'])
    def test_constraint_name_changed(self): self.assertFalse(self.compare(ddl(),ddl(NEW).replace(b'chk_scope',b'chk_other'))['matches'])
    def test_acl_changed(self): self.assertFalse(self.compare(ddl(),ddl(NEW)+b'GRANT ALL ON public.x TO PUBLIC;')['matches'])
    def test_rls_changed(self): self.assertFalse(self.compare(ddl()+b'ALTER TABLE public.x FORCE ROW LEVEL SECURITY;',ddl(NEW))['matches'])
    def test_policy_not_normalized(self): self.assertFalse(self.compare(b'CREATE POLICY x ON public.x USING (scope = '+OLD+b');',b'CREATE POLICY x ON public.x USING (scope = '+NEW+b');')['matches'])
    def test_function_body_not_normalized(self):
        a=b'CREATE FUNCTION public.f() RETURNS text AS $$\n'+ddl()+b'$$ LANGUAGE sql;'; b=a.replace(OLD,NEW); self.assertFalse(self.compare(a,b)['matches'])
    def test_other_schema_not_normalized(self): self.assertFalse(self.compare(ddl().replace(b'public.x',b'other.x'),ddl(NEW).replace(b'public.x',b'other.x'))['matches'])
    def test_string_not_normalized(self):
        a=b"CREATE TABLE public.x (\n    note text DEFAULT $q$\n    CONSTRAINT chk_scope CHECK (scope = "+OLD+b")\n$q$\n);"; self.assertFalse(self.compare(a,a.replace(OLD,NEW))['matches'])
    def test_idempotent(self):
        a,n=m.canonical_schema(ddl()); self.assertEqual((a,0),m.canonical_schema(a)); self.assertEqual(n,1)
    def test_unknown_type(self): self.assertFalse(self.compare(ddl(OLD.replace(b'character varying',b'integer')),ddl(NEW.replace(b'character varying',b'integer')))['matches'])
    def test_noncheck(self): self.assertFalse(self.compare(ddl().replace(b' CHECK ',b' OTHER '),ddl(NEW).replace(b' CHECK ',b' OTHER '))['matches'])
    def test_raw_unchanged(self): self.assertTrue(self.compare(ddl(),ddl())['raw_equal'])
if __name__=='__main__': unittest.main()
