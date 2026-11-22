package com.sakhtyar.knowledge.application;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase04SyntheticContractsTest {
    private static final String SHA = "a".repeat(64);
    @Test void fakeOcrPreservesExactPersianTextAndHashes() {
        var a = Phase04FakeOcrEngine.transcribe("page-A".getBytes(), 1, SHA, "كیفیت\u200cسازه ۱۲۳");
        var b = Phase04FakeOcrEngine.transcribe("page-A".getBytes(), 1, SHA, "كیفیت\u200cسازه ۱۲۳");
        assertEquals(a, b);
        assertEquals("FAKE_DETERMINISTIC", a.engine());
        assertEquals(0, Phase04FakeOcrEngine.characterErrorRate("کاف", "کاف"));
        assertTrue(Phase04FakeOcrEngine.characterErrorRate("کاف", "کبف") > 0);
        assertThrows(SecurityException.class, () -> Phase04FakeOcrEngine.transcribe(new byte[0],0,SHA,"x"));
    }
    @Test void reviewRejectsSeparationViolationAndVersionConflict() {
        var ledger = new Phase04SyntheticReviewCheckpoint();
        UUID id = UUID.randomUUID();
        assertThrows(SecurityException.class, () -> ledger.review(id,0,"reviewer","reviewer","APPROVE",SHA,SHA));
        assertEquals(1, ledger.review(id,0,"author","reviewer","APPROVE",SHA,SHA).version());
        assertThrows(IllegalStateException.class, () -> ledger.review(id,0,"author","other","REJECT",SHA,SHA));
    }
    @Test void checkpointReplayDeduplicatesAndCannotCrossRun() {
        var engine = new Phase04SyntheticReviewCheckpoint();
        UUID workspace = UUID.randomUUID(), run = UUID.randomUUID(), other = UUID.randomUUID();
        engine.start(workspace, run, "OCR_REVIEW",2);
        engine.record(workspace, run, "OCR_REVIEW", "page-1");
        engine.record(workspace, run, "OCR_REVIEW", "page-1");
        assertEquals(1,engine.transition(workspace,run,"OCR_REVIEW",
            Phase04SyntheticReviewCheckpoint.State.PAUSED).completedUniqueItems());
        engine.start(workspace,run,"OCR_REVIEW",2);
        assertEquals(1, engine.record(workspace,run,"OCR_REVIEW","page-1").completedUniqueItems());
        assertEquals(0,engine.open(workspace,other,"OCR_REVIEW").completedUniqueItems());
        assertThrows(IllegalStateException.class,()->engine.start(workspace,run,"OCR_REVIEW",2));
    }
    @Test void syntheticRetrievalIsWorkspaceAndVersionScoped() {
        var index = new Phase04SyntheticRetrievalIndex();
        UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        index.add(new Phase04SyntheticRetrievalIndex.Citation(a,"v1",SHA,1,"متن درباره‌ی سازه", "SYNTHETIC"));
        assertEquals(1,index.search(a,"v1","سازه").size());
        assertTrue(index.search(b,"v1","سازه").isEmpty());
        assertTrue(index.search(a,"v2","سازه").isEmpty());
    }
}
