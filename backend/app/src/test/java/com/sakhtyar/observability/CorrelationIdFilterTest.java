package com.sakhtyar.observability;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesSafeIncomingId() throws Exception {
        var req = new MockHttpServletRequest();
        req.addHeader(CorrelationIdFilter.HEADER, "case-123.trace_1");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, (a, b) -> {});
        assertThat(res.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("case-123.trace_1");
    }

    @Test
    void replacesUnsafeIncomingId() throws Exception {
        var req = new MockHttpServletRequest();
        req.addHeader(CorrelationIdFilter.HEADER, "bad\nvalue");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, (a, b) -> {});
        assertThat(res.getHeader(CorrelationIdFilter.HEADER)).isNotBlank().doesNotContain("\n");
    }
}
