package com.sakhtyar.identity.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Phase03AllowedOriginsTest {
    @Test void exactLocalAndHttpsOriginsAcceptedAndDeduplicated() {
        assertEquals(List.of("http://localhost:5175", "https://app.example.test"),
                Phase03AllowedOrigins.parse("http://localhost:5175, https://app.example.test,http://localhost:5175"));
    }
    @Test void wildcardAndNonLoopbackHttpRejected() {
        assertThrows(IllegalArgumentException.class, () -> Phase03AllowedOrigins.parse("*"));
        assertThrows(IllegalArgumentException.class, () -> Phase03AllowedOrigins.parse("http://evil.example.test"));
    }
    @Test void credentialsAndPathsAndQueryRejected() {
        for (String origin : List.of("https://user:pass@app.example.test", "https://app.example.test/evil",
                "https://app.example.test?x=1", "file:///etc/passwd", "null")) {
            assertThrows(IllegalArgumentException.class, () -> Phase03AllowedOrigins.parse(origin), origin);
        }
    }
}
