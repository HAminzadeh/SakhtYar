package com.sakhtyar.knowledge.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class Phase17BReleaseCompatibilityTest {
    @Test void emptyComponentsCannotBePublished() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        KnowledgeReleaseService service=new KnowledgeReleaseService(jdbc);
        UUID releaseId=UUID.randomUUID();
        when(jdbc.queryForList(anyString(),eq(releaseId))).thenReturn(List.of());
        assertEquals(false,service.compatibility(releaseId).get("compatible"));
    }
}
