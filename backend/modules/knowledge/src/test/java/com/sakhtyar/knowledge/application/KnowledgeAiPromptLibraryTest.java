package com.sakhtyar.knowledge.application;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeAiPromptLibraryTest {
    @Test void lawPromptUsesStableSchema() {
        String prompt=KnowledgeAiPromptLibrary.prompt("LAW");
        assertTrue(prompt.contains("manifest.json"));
        assertTrue(prompt.contains("rules.jsonl"));
        assertTrue(prompt.contains("sakhtyar-ai-knowledge-v1"));
        assertTrue(prompt.contains("HUMAN_REQUIRED"));
    }
    @Test void educationPromptNeverCreatesLegalRules() {
        String prompt=KnowledgeAiPromptLibrary.prompt("EDUCATION");
        assertTrue(prompt.contains("\"category\":\"EDUCATION\""));
        assertTrue(prompt.contains("rules.jsonl"));
        assertTrue(prompt.contains("کاملاً خالی"));
    }
    @Test void unsupportedModeFails() {
        assertThrows(IllegalArgumentException.class,()->KnowledgeAiPromptLibrary.prompt("OTHER"));
    }
}
