package com.sakhtyar.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Phase03ClientIpBoundaryTest {
    @Test void forgedForwardedForDoesNotOverrideVerifiedServletAddress() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRemoteAddr()).thenReturn("192.0.2.15");
        when(req.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 192.0.2.15");
        assertEquals("192.0.2.15", AuthController.clientIp(req));
        verify(req,never()).getHeader("X-Forwarded-For");
    }
}
