package com.sakhtyar.config;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase06ConfigSandboxTest {
    private static Phase06ConfigSandbox fresh() {
        return new Phase06ConfigSandbox(UUID.fromString("00000000-0000-4000-8000-000000000111"),
                UUID.fromString("00000000-0000-4000-8000-000000000222"));
    }
    @Test void orderedDraftReviewPublishRollbackWithStableHash() {
        var c=fresh();var draft=c.draft("editor",Map.of("test.rate","12", "test.market","FAKE"));
        assertEquals(Phase06ConfigSandbox.Status.DRAFT,draft.status());
        assertThrows(IllegalStateException.class,()->c.publishInSandboxOnly(draft.version(),"reviewer"));
        assertThrows(SecurityException.class,()->c.review(draft.version(),"editor"));
        var reviewed=c.review(draft.version(),"reviewer");
        var published=c.publishInSandboxOnly(reviewed.version(),"reviewer");
        assertEquals(Phase06ConfigSandbox.Status.PUBLISHED,published.status());
        assertEquals(draft.sha256(),published.sha256());
        assertThrows(SecurityException.class,()->c.rollbackInSandboxOnly(draft.version(),"editor"));
        assertEquals(Phase06ConfigSandbox.Status.ROLLED_BACK,
                c.rollbackInSandboxOnly(published.version(),"reviewer").status());
        assertEquals(Map.of("test.market","FAKE","test.rate","12"),c.get(draft.version()).values());
    }
    @Test void rejectSecretsAndCrossRunScope() {
        var s=fresh();
        assertThrows(SecurityException.class,()->s.draft("editor",Map.of("production.secret","hidden")));
        assertThrows(SecurityException.class,()->s.draft("editor",Map.of("test.apiKey","hidden")));
        assertThrows(IllegalArgumentException.class,()->s.draft("editor",null));
        assertThrows(IllegalArgumentException.class,()->new Phase06ConfigSandbox(UUID.randomUUID(),null));
        assertThrows(IllegalArgumentException.class,()->s.get(999));
    }
    @Test void immutableSnapshotsAndDeterministicHash() {
        var a=fresh().draft("editor",Map.of("test.a","one","test.b","two"));
        var b=fresh().draft("editor",Map.of("test.b","two","test.a","one"));
        assertEquals(a.sha256(),b.sha256());
        assertThrows(UnsupportedOperationException.class,()->a.values().put("test.a","tamper"));
    }
}
