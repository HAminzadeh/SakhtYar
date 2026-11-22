package com.sakhtyar.casefile.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sakhtyar.casefile.api.CaseController;
import com.sakhtyar.casefile.api.CaseDtos.CaseResponse;
import com.sakhtyar.casefile.api.CaseDtos.UpsertCaseRequest;
import com.sakhtyar.casefile.domain.CaseStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class Phase08CaseHttpContractTest {
    @Test void getEmitsEtagWithoutChangingResponseBody() {
        CaseService service = mock(CaseService.class);
        CaseController controller = new CaseController(service);
        UUID id = UUID.randomUUID();
        Instant updated = Instant.parse("2026-11-22T07:30:00.123456Z");
        CaseResponse item = new CaseResponse(id, "SYF-123", "case", CaseStatus.DRAFT,
                null, null, null, null, null, null, "owner", updated, updated);
        when(service.get(id)).thenReturn(item);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertSame(item, controller.get(id, response));
        assertEquals(CaseRevision.etag(updated), response.getHeader(HttpHeaders.ETAG));
    }

    @Test void putForwardsClientIfMatchToTransactionalService() {
        CaseService service = mock(CaseService.class);
        CaseController controller = new CaseController(service);
        UUID id = UUID.randomUUID();
        Instant updated = Instant.parse("2026-11-22T07:30:00.123456Z");
        UpsertCaseRequest req = new UpsertCaseRequest("title", CaseStatus.DRAFT,
                null, null, null, null, null, null);
        CaseResponse item = new CaseResponse(id, "SYF-123", "title", CaseStatus.DRAFT,
                null, null, null, null, null, null, "owner", updated, updated);
        String tag = CaseRevision.etag(updated);
        when(service.update(id, req, tag)).thenReturn(item);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertSame(item, controller.update(id, req, tag, response));
        verify(service).update(id, req, tag);
        assertEquals(tag, response.getHeader(HttpHeaders.ETAG));
    }
}
