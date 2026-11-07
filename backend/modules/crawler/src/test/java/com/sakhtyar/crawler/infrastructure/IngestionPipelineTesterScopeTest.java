package com.sakhtyar.crawler.infrastructure;

import com.sakhtyar.crawler.application.IngestionPipeline;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.junit.jupiter.api.Assertions.*;

class IngestionPipelineTesterScopeTest {
    @Test void rejectsTesterBeforeDatabaseOrNetworkAccess() {
        var tester = new UsernamePasswordAuthenticationToken("tester", null,
            List.of(new SimpleGrantedAuthority("ROLE_TESTER")));
        var runner = new IngestionPipeline(null, null, null, null, null, null, null, null, null, null, null);
        assertThrows(SecurityException.class,
            () -> runner.run(UUID.randomUUID(), 1, tester));
    }
}
