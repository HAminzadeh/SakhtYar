import unittest
from sakhtyar_persian.normalizer import normalize_light
from sakhtyar_persian.legal_guard import preserves
from sakhtyar_persian.quality import analyze

class CoreTests(unittest.TestCase):
    def test_arabic_chars(self):
        self.assertEqual(normalize_light("كتاب شهري"), "کتاب شهری")
    def test_legal_spans(self):
        x="طبق ماده ۱۴ در پهنه R122 سطح اشغال 60 درصد است."
        self.assertTrue(preserves(x, normalize_light(x)))
    def test_bad_text_scores_lower(self):
        self.assertLess(analyze("تهر ان و ش ورای و کژه").score, analyze("تهران و شورای و که").score)

if __name__ == "__main__":
    unittest.main()