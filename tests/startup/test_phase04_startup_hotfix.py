"""Source contract regression; no Docker engine, database, or network required."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]


class StartupHotfixContractTest(unittest.TestCase):
    def test_mandatory_services_remain_fail_closed(self):
        src = (ROOT / 'backend/app/src/main/java/com/sakhtyar/bootstrap/LocalInfrastructurePolicy.java').read_text(encoding='utf-8')
        self.assertIn('"db"', (ROOT / 'backend/app/src/main/java/com/sakhtyar/bootstrap/LocalInfrastructureInitializer.java').read_text(encoding='utf-8'))
        self.assertIn('"redis"', (ROOT / 'backend/app/src/main/java/com/sakhtyar/bootstrap/LocalInfrastructureInitializer.java').read_text(encoding='utf-8'))
        self.assertIn('"minio"', (ROOT / 'backend/app/src/main/java/com/sakhtyar/bootstrap/LocalInfrastructureInitializer.java').read_text(encoding='utf-8'))
        self.assertIn('return !OPTIONAL_OBSERVABILITY.contains(service);', src)
        self.assertIn('"running|healthy".equals(state)', src)

    def test_tempo_retention_does_not_use_removed_compactor_block(self):
        src = (ROOT / 'deploy/observability/tempo.yml').read_text(encoding='utf-8')
        self.assertNotIn('\ncompactor:', src)
        self.assertIn('overrides:\n  defaults:\n    compaction:\n      block_retention: 168h', src)
        self.assertIn('metrics_generator:', src)

    def test_core_bootstrap_is_independent_of_optional_services(self):
        src = (ROOT / 'backend/app/src/main/java/com/sakhtyar/bootstrap/LocalInfrastructureInitializer.java').read_text(encoding='utf-8')
        self.assertIn('requiredArgs.addAll(required)', src)
        self.assertIn('optionalArgs.addAll(optional)', src)
        self.assertIn('observability may be degraded', src)
        self.assertIn('if (failedRequired)', src)
        self.assertNotIn('if (failed) {', src)

    def test_xml_order_conforms_to_xs_sequence(self):
        xsd = (ROOT / 'backend/modules/shared-kernel/src/main/resources/contracts/urban-query-v1.xsd').read_text(encoding='utf-8')
        test = (ROOT / 'backend/modules/shared-kernel/src/test/java/com/sakhtyar/shared/xml/Phase04XmlSecurityTest.java').read_text(encoding='utf-8')
        self.assertLess(xsd.index('name="LandAreaM2"'), xsd.index('name="ZoneCode"'))
        self.assertIn('<Property><LandAreaM2>123.450</LandAreaM2><ZoneCode>منطقه ۱۲</ZoneCode></Property>', test)

    def test_no_destructive_install_commands_are_added(self):
        src = (ROOT / 'backend/app/src/main/java/com/sakhtyar/bootstrap/LocalInfrastructureInitializer.java').read_text(encoding='utf-8')
        for destructive in ('"down"', '"volume"', '"prune"', '"rm"', '"delete"'):
            self.assertNotIn(destructive, src)


if __name__ == '__main__':
    unittest.main()
