"""Dependency-free adaptation of Hazm SentenceTokenizer only.
Upstream: https://github.com/roshan-research/hazm/blob/master/hazm/sentence_tokenizer.py
MIT license (Copyright (c) 2013 Alireza Nourian); see HAZM-SENTENCE-MIT-LICENSE.txt.
This module IS NOT the full Hazm package. It preserves the sentence-tokenization
algorithm required for SakhtYar legal-segment processing without importing NLTK.
"""
import re


class SentenceTokenizer:
    def __init__(self):
        self.pattern = re.compile(r"([!.?⸮؟]+)[ \n]+")

    def tokenize(self, text):
        text = self.pattern.sub(r"\1\n\n", text)
        return [sentence.replace("\n", " ").strip()
                for sentence in text.split("\n\n") if sentence.strip()]
