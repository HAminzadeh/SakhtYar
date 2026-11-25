package com.sakhtyar.knowledge.governance;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RegulationCandidatePolicyTest {
    @Test void normalizesArabicAndPersianDigits() {
        assertEquals("حداکثر 60 درصد سطح اشغال", RegulationCandidatePolicy.normalize("حداكثر ۶۰ درصد سطح اشغال"));
        assertEquals("12.5", RegulationCandidatePolicy.normalize("۱۲٫۵"));
        assertEquals("12", RegulationCandidatePolicy.normalize("١٢"));
    }
    @Test void offersOnlyAReviewableSuggestion() {
        var p=RegulationCandidatePolicy.propose("حداکثر سطح اشغال ۶۰ درصد است");
        assertEquals("MAX_COVERAGE_PERCENT",p.ruleType());
        assertEquals("<=",p.operator());
        assertEquals("60",p.numericValue());
        assertEquals("PERCENT",p.unit());
        assertEquals("PROPOSED_ONLY",p.reviewState());
        assertEquals("LOW_HEURISTIC_ONLY",p.parserConfidence());
        assertTrue(p.warnings().contains("HUMAN_LEGAL_REVIEW_REQUIRED"));
    }
    @Test void refusesAmbiguousNumbersAndSemantics() {
        var p=RegulationCandidatePolicy.propose("حداکثر ۵ طبقه در قطعه ۲ است");
        assertNull(p.ruleType());
        assertTrue(p.warnings().contains("MULTIPLE_NUMBERS"));
        assertNull(RegulationCandidatePolicy.propose("ضوابط تراکم ۶۰ درصد").ruleType());
        assertNull(RegulationCandidatePolicy.propose("بیشتر از ۵ طبقه").ruleType());
        assertNull(RegulationCandidatePolicy.propose("کمتر از ۳ طبقه").ruleType());
        assertNull(RegulationCandidatePolicy.propose("ماده بدون عدد و شرط").ruleType());
    }
    @Test void producesSafeReviewForMissingOrPunctuation() {
        assertEquals("REVIEW_REQUIRED",RegulationCandidatePolicy.propose("  ").reviewState());
        assertEquals("REVIEW_REQUIRED",RegulationCandidatePolicy.propose("حداکثر ارتفاع ۱٬۲۰۰ متر").reviewState());
    }
    @Test void fingerprintsAreStableAndVersionSensitive() {
        String a=RegulationCandidatePolicy.fingerprint("item-1","abc","ماده ۱");
        assertEquals(a,RegulationCandidatePolicy.fingerprint("item-1","ABC","ماده 1"));
        assertNotEquals(a,RegulationCandidatePolicy.fingerprint("item-1","def","ماده ۱"));
        assertNotEquals(a,RegulationCandidatePolicy.fingerprint("item-2","abc","ماده ۱"));
        assertThrows(IllegalArgumentException.class,()->RegulationCandidatePolicy.fingerprint("","hash","key"));
    }
    @Test void boundedTextParserDoesNotRecognizeEveryNumberAsLaw() {
        assertNull(RegulationCandidatePolicy.propose("طبق سند شماره ۱۲۳، پروژه ساخته شود").ruleType());
        assertEquals("MIN_PASSAGE_WIDTH",RegulationCandidatePolicy.propose("حداقل عرض گذر ۸ متر است").ruleType());
        assertEquals("MAX_FLOORS",RegulationCandidatePolicy.propose("حداکثر ۵ طبقه").ruleType());
    }
}
