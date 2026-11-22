"""Offline positive/negative source gate tests. No real database or HTTP connections."""
import importlib.util
import pathlib
import tempfile
import unittest

MODULE_FILE=pathlib.Path(__file__).resolve().parents[2]/'scripts/contracts/phase07_api_inventory.py'
spec=importlib.util.spec_from_file_location('phase07_inventory',MODULE_FILE)
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def write(root,path,data):
    target=root/path
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(data,encoding='utf-8')


def fixture(root):
    # Reflect the known golden surface without pretending to test business logic.
    routes=[('/api/v1/auth', [('PostMapping','/login'),('PostMapping','/refresh'),
                             ('GetMapping','/me'),('GetMapping','/csrf')]),
            ('/api/v1/cases',[('GetMapping',''),('PostMapping',''),('GetMapping','/{id}')]),
            ('/api/v1/cases/{caseId}/property',[('GetMapping',''),('PutMapping','')]),
            ('/api/v1/cases/{caseId}/owners',[('GetMapping',''),('PostMapping','')]),
            ('/api/v1/geo',[('GetMapping','/geocode'),('GetMapping','/reverse')]),
            ('/api/v1/admin/audit',[('GetMapping','/events')]),
            ('/api/v1/admin/quality',[('GetMapping','/catalog')])]
    for i,(base,methods) in enumerate(routes):
        body='@RestController\n@RequestMapping("'+base+'")\npublic class C'+str(i)+' {\n'
        for j,(annotation,sub) in enumerate(methods):
            body+='@'+annotation+'("'+sub+'")\npublic Object f'+str(j)+'(){return null;}\n'
        body+='}\n'
        write(root,f'backend/modules/f{i}/src/main/java/C{i}.java',body)
    write(root,'frontend/src/pages/Example.tsx', '''import { api } from '../api/client'
export async function run() {
 return api<any>('/api/v1/cases')
}
''')
    for enum,vals in {
        'Permission':['CASE_READ','CASE_WRITE','QUALITY_VIEW','QUALITY_RUN_SAFE','USER_MANAGE'],
        'UserRole':['ADMIN','TESTER','READ_ONLY'],
        'UserStatus':['ACTIVE','PENDING','SUSPENDED'],
        'CaseStatus':['DRAFT','ACTIVE','COMPLETED']}.items():
        mod='casefile' if enum=='CaseStatus' else 'identity'
        write(root,f'backend/modules/{mod}/src/main/java/com/sakhtyar/{mod}/domain/{enum}.java',
              'public enum '+enum+' { '+', '.join(vals)+'; }')
    unions=[f'export type {e} = '+'\n  | '+'\n  | '.join("'"+x+"'" for x in vals) for e,vals in {
        'Permission':['CASE_READ','CASE_WRITE','QUALITY_VIEW','QUALITY_RUN_SAFE','USER_MANAGE'],
        'UserRole':['ADMIN','TESTER','READ_ONLY'],
        'UserStatus':['ACTIVE','PENDING','SUSPENDED'],
        'CaseStatus':['DRAFT','ACTIVE','COMPLETED']}.items()]
    write(root,'frontend/src/api/types.ts','\n\n'.join(unions)+'\n\n')


class Phase07InventoryTests(unittest.TestCase):
    def test_balanced_annotations_and_class_paths(self):
        with tempfile.TemporaryDirectory() as td:
            root=pathlib.Path(td)
            write(root,'backend/modules/sample/src/main/java/Sample.java','''
@RestController @RequestMapping("/api/v1/cases") public class Sample {
 @GetMapping(value="/{caseId}", produces="application/json") Object get(){return null;}
 @PutMapping("/{caseId}") Object put(){return null;}
}
''')
            routes,unresolved=m.java_routes(root/'backend/modules',root)
            self.assertEqual(2,len(routes))
            self.assertEqual([],unresolved)
            self.assertTrue(m.route_matches({'method':'GET','path':'/api/v1/cases/abc'},routes))
            self.assertFalse(m.route_matches({'method':'POST','path':'/api/v1/cases/abc'},routes))

    def test_golden_and_enums_pass_only_with_actual_source_fixtures(self):
        with tempfile.TemporaryDirectory() as td:
            root=pathlib.Path(td); fixture(root)
            result=m.audit(root)
            self.assertEqual('PASS_SOURCE_GATES_RUNTIME_UNVERIFIED',result['status'])
            self.assertEqual(len(m.GOLDEN),result['confirmed_golden'])
            self.assertFalse(result['runtime_api_verified'])
            self.assertFalse(result['postgresql_isolation_verified'])

    def test_missing_backend_golden_blocks(self):
        with tempfile.TemporaryDirectory() as td:
            root=pathlib.Path(td);fixture(root)
            (root/'backend/modules/f2/src/main/java/C2.java').unlink()
            result=m.audit(root)
            self.assertEqual('BLOCKED_SOURCE_DRIFT',result['status'])
            self.assertTrue(any('property' in x for x in result['failed']))

    def test_enum_drift_blocks(self):
        with tempfile.TemporaryDirectory() as td:
            root=pathlib.Path(td);fixture(root)
            p=root/'frontend/src/api/types.ts'
            p.write_text(p.read_text('utf-8').replace("  | 'ADMIN'", "  | 'ROOT_ADMIN'"),'utf-8')
            result=m.audit(root)
            self.assertIn('ENUM_DRIFT:UserRole',result['failed'])

    def test_dynamic_and_missing_frontend_calls_not_automatically_claimed_passed(self):
        with tempfile.TemporaryDirectory() as td:
            root=pathlib.Path(td);fixture(root)
            write(root,'frontend/src/pages/Missing.tsx',"const x=api('/api/v1/does-not-exist')\n")
            result=m.audit(root)
            self.assertTrue(any('does-not-exist' in x['path'] for x in result['possible_unmatched_calls']))
            self.assertFalse(result['http_integration_verified'])

    def test_no_source_mutation(self):
        with tempfile.TemporaryDirectory() as td:
            root=pathlib.Path(td);fixture(root)
            before={p.relative_to(root).as_posix():p.read_bytes() for p in root.rglob('*') if p.is_file()}
            m.audit(root)
            self.assertEqual(before,{p.relative_to(root).as_posix():p.read_bytes() for p in root.rglob('*') if p.is_file()})

    def test_openapi_draft_is_labeled_unverified(self):
        spec=m.openapi_draft([{'method':'GET','path':'/api/v1/cases','source':'C.java'}],'demo')
        self.assertEqual('UNVERIFIED_RUNTIME',spec['paths']['/api/v1/cases']['get']['x-contract-status'])
        self.assertIn('REAL/TEST isolation',spec['x-not-proven'])


if __name__=='__main__': unittest.main()
