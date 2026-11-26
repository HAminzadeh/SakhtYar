package com.sakhtyar.knowledge.governance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class RegulationCandidateBridgeAuthorizationTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final RegulationCandidateBridgeService service=new RegulationCandidateBridgeService(jdbc);
    @AfterEach void clear() {SecurityContextHolder.clearContext();}

    @Test void anonymousCannotReadStaging() {
        assertThrows(AccessDeniedException.class,()->service.candidates(1,20,"ALL"));
        verifyNoInteractions(jdbc);
    }
    @Test void reviewerWithoutManagerPermissionCannotReadStaging() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "reader","n/a",List.of(new SimpleGrantedAuthority("CASE_READ"))));
        assertThrows(AccessDeniedException.class,()->service.candidates(1,20,"ALL"));
        verifyNoInteractions(jdbc);
    }
    @Test void managerSeesSuggestionsButNoCanonicalWrite() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin","n/a",List.of(new SimpleGrantedAuthority("USER_MANAGE"))));
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any())).thenReturn(1L);
        Map<String,Object> item=new HashMap<>();
        item.put("item_id",UUID.randomUUID());item.put("content_sha256","hash");
        item.put("full_text","حداکثر ۵ طبقه");item.put("source_matches",0L);
        item.put("segment_type","ARTICLE");
        // Force the varargs SQL overload, not queryForList(String, Class<T>, Object...).
        when(jdbc.queryForList(anyString(), eq("RULE"), eq("RULE"), eq(20), eq(0)))
                .thenReturn(List.of(item));
        Map<String,Object> page=service.candidates(1,20,"RULE");
        assertEquals("NONE",page.get("publication"));
        @SuppressWarnings("unchecked") var items=(List<Map<String,Object>>)page.get("items");
        assertEquals(1, items.size(), "The mocked SQL result must contain one candidate");
        assertEquals("BLOCKED_MISSING_SOURCE",items.get(0).get("provenanceState"));
        assertEquals("HUMAN_REVIEW_REQUIRED",items.get(0).get("reviewState"));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}
