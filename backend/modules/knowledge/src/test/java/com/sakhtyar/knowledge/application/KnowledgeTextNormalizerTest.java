package com.sakhtyar.knowledge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class KnowledgeTextNormalizerTest {

    @Test
    void normalizesArabicVariantsAndWhitespace() {
        assertEquals(
                "\u0645\u0634\u0627\u0631\u06A9\u062A \u062F\u0631 \u0633\u0627\u062E\u062A",
                KnowledgeTextNormalizer.normalize(
                        "  \u0645\u0634\u0627\u0631\u0643\u062A\u200C \u062F\u0631   \u0633\u0627\u062E\u062A  "
                )
        );
    }

    @Test
    void removesArabicDiacritics() {
        assertEquals(
                "\u0645\u0627\u0644\u06A9",
                KnowledgeTextNormalizer.normalize(
                        "\u0645\u0627\u0644\u0650\u06A9"
                )
        );
    }

    @Test
    void handlesNullAndBlankInput() {
        assertEquals("", KnowledgeTextNormalizer.normalize(null));
        assertEquals("", KnowledgeTextNormalizer.normalize("   "));
    }
}