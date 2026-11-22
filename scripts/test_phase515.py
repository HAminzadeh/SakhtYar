#!/usr/bin/env python3
"""Phase515 offline regression tests of review gate evaluation schema."""
import importlib.util
import unittest
from pathlib import Path
p=Path(__file__).with_name('golden_evaluate.py')
spec=importlib.util.spec_from_file_location('golden_evaluate',p)
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
class GoldenGateTests(unittest.TestCase):
    def test_drafts_do_not_score(self):
        draft={'cases':[{'id':'Q1','question':'question'}]}
        self.assertEqual(module.evaluate(draft)['status'],'DRAFT_NOT_SCORED')
        with self.assertRaisesRegex(ValueError,'No expert-verified'):
            module.evaluate(draft,{'predictions':[]})
    def test_verified_answer_and_citation(self):
        dataset={'cases':[{'id':'Q1','question':'test','expertVerified':True,'expertName':'Independent expert','sourceSha256':'1'*64,'sourcePage':4,'expected':{'value':5,'operator':'<=','unit':'FLOOR','mustAbstain':False}}]}
        result=module.evaluate(dataset,{'predictions':[{'id':'Q1','value':5,'operator':'<=','unit':'FLOOR','sourceSha256':'1'*64,'sourcePage':4,'abstained':False}]})
        self.assertEqual(result['precision'],1.0)
        wrong=module.evaluate(dataset,{'predictions':[{'id':'Q1','value':5,'operator':'<=','unit':'FLOOR','sourceSha256':'2'*64,'sourcePage':4,'abstained':False}]})
        self.assertEqual(wrong['falsePositive'],1)
    def test_abstention(self):
        d={'cases':[{'id':'Q1','question':'unknown','expertVerified':True,'expertName':'Independent expert','sourceSha256':'1'*64,'sourcePage':1,'expected':{'mustAbstain':True}}]}
        self.assertEqual(module.evaluate(d,{'predictions':[{'id':'Q1','abstained':True}]})['abstentionAccuracy'],1.0)
if __name__=='__main__':unittest.main()
