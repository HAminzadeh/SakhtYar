"""Source-only negative tests, no Maven, Docker, network or DB required."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SCRIPT=Path(__file__).resolve().parents[2]/'scripts/contracts/phase04_shared_kernel_contract_audit.py'
spec=importlib.util.spec_from_file_location('p04audit',SCRIPT)
auditmod=importlib.util.module_from_spec(spec)
spec.loader.exec_module(auditmod)

class Phase04SourceContractTest(unittest.TestCase):
    def test_real_source_contracts(self):
        root=Path(__file__).resolve().parents[2]
        result=auditmod.audit(root)
        self.assertEqual('PASS_SOURCE_CONTRACTS_ONLY',result['status'])
        self.assertTrue(result['acyclic'])
        self.assertGreater(result['module_count'],20)
        self.assertFalse(result['database_changed'])

    def test_missing_required_schema_field_rejected(self):
        fake={'type':'object','additionalProperties':False,'required':['schemaVersion'],
              'properties':{'schemaVersion':{'const':'2.0'}}}
        with self.assertRaises(auditmod.ContractViolation):
            auditmod.schema_contract(fake,auditmod.REQUEST_FIELDS,'request')

    def test_schema_version_drift_rejected(self):
        fake={'type':'object','additionalProperties':False,
              'required':sorted(auditmod.REQUEST_FIELDS),
              'properties':{k:{'format':'date-time'} if k=='createdAt' else {} for k in auditmod.REQUEST_FIELDS}}
        fake['properties']['schemaVersion']={'const':'1.0'}
        with self.assertRaises(auditmod.ContractViolation):
            auditmod.schema_contract(fake,auditmod.REQUEST_FIELDS,'request')

    def test_cyclic_dependency_graph_rejected(self):
        with self.assertRaisesRegex(auditmod.ContractViolation,'cycle'):
            auditmod.cycles({'sakhtyar-shared-kernel':set(),'one':{'two'},'two':{'one'}})

    def test_unknown_internal_dependency_rejected(self):
        with self.assertRaisesRegex(auditmod.ContractViolation,'Unknown'):
            auditmod.cycles({'sakhtyar-shared-kernel':set(),'one':{'missing'}})

    def test_invalid_module_parent_version_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp)
            (base/'modules/shared-kernel').mkdir(parents=True)
            (base/'pom.xml').write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0"><version>0.2.0-SNAPSHOT</version>
              <modules><module>modules/shared-kernel</module></modules></project>''')
            (base/'modules/shared-kernel/pom.xml').write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0">
              <parent><version>999.0</version></parent><artifactId>sakhtyar-shared-kernel</artifactId></project>''')
            with self.assertRaisesRegex(auditmod.ContractViolation,'version drift'):
                auditmod.load_maven_graph(base)

    def test_missing_module_is_blocked(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp)
            (base/'pom.xml').write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0"><version>0.2.0-SNAPSHOT</version>
              <modules><module>modules/shared-kernel</module></modules></project>''')
            with self.assertRaisesRegex(auditmod.ContractViolation,'missing'):
                auditmod.load_maven_graph(base)

if __name__=='__main__':unittest.main()
