package com.sakhtyar.knowledge.application;

import java.text.Normalizer;
import java.util.Locale;

public final class KnowledgeTextNormalizer {

    private KnowledgeTextNormalizer() {}

    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        return Normalizer.normalize(input, Normalizer.Form.NFKC)
                .replace('\u064A', '\u06CC')
                .replace('\u0649', '\u06CC')
                .replace('\u0643', '\u06A9')
                .replace('\u200C', ' ')
                .replaceAll("[\\u064B-\\u065F\\u0670]", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.forLanguageTag("fa"));
    }
}