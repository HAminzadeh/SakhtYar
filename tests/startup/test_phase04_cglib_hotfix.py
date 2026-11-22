"""Offline negative contract tests; no Docker, database, network or Java dependencies."""
import pathlib
import tempfile
import unittest
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts' / 'startup'))
from phase04_cglib_readonly_audit import audit, TARGET, TEST  # noqa: E402

VALID = '''
@RestController
public class Phase02SharedDatabaseAuditController {
  @GetMapping("/api/v1/admin/quality/database-isolation")
  @Transactional(readOnly = true)
  public Map inspect(Authentication authentication) {
    if ("USER_MANAGE".equals("ROLE_TESTER")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    jdbc.queryForList(CATALOG_SQL); jdbc.queryForMap(PRINCIPAL_SQL);
    /* pg_catalog */
    result.put("globalIsolationVerified", false);
    result.put("businessE2eWritesAuthorized", false);
    return result;
  }
}
'''


class StartupContractTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        target = self.root / TARGET
        target.parent.mkdir(parents=True)
        target.write_text(VALID, encoding='utf-8')
        test = self.root / TEST
        test.parent.mkdir(parents=True)
        test.write_text('AopUtils.isCglibProxy(proxy)', encoding='utf-8')

    def tearDown(self):
        self.tmp.cleanup()

    def test_known_good_contract_passes(self):
        self.assertEqual('PASS', audit(self.root)['status'])

    def test_final_controller_fails_closed(self):
        p = self.root / TARGET
        p.write_text(VALID.replace('public class', 'public final class'), encoding='utf-8')
        self.assertEqual('BLOCKED', audit(self.root)['status'])

    def test_transaction_not_readonly_fails_closed(self):
        p = self.root / TARGET
        p.write_text(VALID.replace('@Transactional(readOnly = true)', '@Transactional'), encoding='utf-8')
        self.assertEqual('BLOCKED', audit(self.root)['status'])

    def test_permissions_missing_fails_closed(self):
        p = self.root / TARGET
        p.write_text(VALID.replace('USER_MANAGE', 'QUALITY_VIEW'), encoding='utf-8')
        self.assertEqual('BLOCKED', audit(self.root)['status'])

    def test_missing_regression_test_fails_closed(self):
        (self.root / TEST).unlink()
        self.assertEqual('BLOCKED', audit(self.root)['status'])

    def test_missing_controller_fails_closed(self):
        (self.root / TARGET).unlink()
        self.assertEqual('BLOCKED', audit(self.root)['status'])


if __name__ == '__main__':
    unittest.main()
