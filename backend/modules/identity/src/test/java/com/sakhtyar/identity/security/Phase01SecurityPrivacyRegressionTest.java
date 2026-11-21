package com.sakhtyar.identity.security;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sakhtyar.identity.domain.UserRole;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.sakhtyar.identity.domain.Permission;

class Phase01SecurityPrivacyRegressionTest {
    @Test
    void testRoleHasOnlySafeQualityPermissions() {
        assertEquals(Set.of(Permission.QUALITY_VIEW, Permission.QUALITY_RUN_SAFE),
                UserRole.TESTER.permissions());
        assertFalse(UserRole.TESTER.has(Permission.USER_MANAGE));
        assertFalse(UserRole.TESTER.has(Permission.CASE_WRITE));
        assertFalse(UserRole.TESTER.has(Permission.DOCUMENT_WRITE));
        assertFalse(UserRole.TESTER.has(Permission.AI_MANAGE));
    }

    @Test
    void securityDiagnosticsNeverLogsPrincipalCookieOrTokenFingerprint() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(SecurityDiagnosticsFilter.class);
        Level oldLevel = logger.getLevel();
        ListAppender<ILoggingEvent> capture = new ListAppender<>();
        capture.start();
        logger.addAppender(capture);
        logger.setLevel(Level.DEBUG);
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("private-user@example.test", null,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
            request.setCookies(new Cookie("sakhtyar_access", "hidden-access-secret"),
                    new Cookie("XSRF-TOKEN", "hidden-xsrf-secret"));
            request.addHeader("Authorization", "Bearer hidden-header-secret");
            request.setAttribute(JwtCookieAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE, "JWT_INVALID_OR_EXPIRED");
            MockHttpServletResponse response = new MockHttpServletResponse();
            new SecurityDiagnosticsFilter().doFilter(request, response,
                    (req, res) -> ((MockHttpServletResponse) res).setStatus(401));
            String logs = capture.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertTrue(logs.contains("status=401"));
            assertTrue(logs.contains("JWT_INVALID_OR_EXPIRED"));
            for (String sensitive : List.of("private-user@example.test", "hidden-access-secret",
                    "hidden-xsrf-secret", "hidden-header-secret", "ROLE_ADMIN", "principal=")) {
                assertFalse(logs.contains(sensitive), "Privacy regression: " + sensitive);
            }
        } finally {
            SecurityContextHolder.clearContext();
            logger.detachAppender(capture);
            logger.setLevel(oldLevel);
            capture.stop();
        }
    }
}
