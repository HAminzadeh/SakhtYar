package com.sakhtyar.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SecurityDiagnosticsFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(SecurityDiagnosticsFilter.class);

    private static final Set<String> EXACT_PATHS = Set.of(
            "/api/v1/auth/csrf",
            "/api/v1/auth/refresh",
            "/api/v1/auth/me",
            "/api/v1/global/preferences/me"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !EXACT_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId =
                Integer.toHexString(System.identityHashCode(request));

        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();

        String accessCookie =
                cookie(request, JwtCookieAuthenticationFilter.ACCESS_COOKIE_NAME);
        String refreshCookie =
                cookie(request, JwtCookieAuthenticationFilter.REFRESH_COOKIE_NAME);
        String xsrfCookie =
                cookie(request, "XSRF-TOKEN");

        String xsrfHeader =
                request.getHeader("X-XSRF-TOKEN");
        String csrfHeader =
                request.getHeader("X-CSRF-TOKEN");

        log.info(
                "[SECURITY-TRACE:{}] BEFORE method={} path={} auth={} principal={} authorities={} accessCookie={} refreshCookie={} xsrfCookieFp={} xXsrfHeaderFp={} xCsrfHeaderFp={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                auth != null && auth.isAuthenticated(),
                auth == null ? "-" : auth.getName(),
                auth == null ? "-" : auth.getAuthorities(),
                present(accessCookie),
                present(refreshCookie),
                fingerprint(xsrfCookie),
                fingerprint(xsrfHeader),
                fingerprint(csrfHeader)
        );

        try {
            filterChain.doFilter(request, response);
        } finally {
            Authentication after =
                    SecurityContextHolder.getContext().getAuthentication();

            String csrfFailure = String.valueOf(
                    request.getAttribute(
                            JwtCookieAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE
                    )
            );

            String setCookieXsrf =
                    xsrfCookieFromSetCookie(response.getHeaders("Set-Cookie"));

            log.info(
                    "[SECURITY-TRACE:{}] AFTER method={} path={} status={} auth={} principal={} authFailure={} responseXsrfCookieFp={}",
                    requestId,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    after != null && after.isAuthenticated(),
                    after == null ? "-" : after.getName(),
                    "null".equals(csrfFailure) ? "-" : csrfFailure,
                    fingerprint(setCookieXsrf)
            );
        }
    }

    private static String cookie(
            HttpServletRequest request,
            String name
    ) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String xsrfCookieFromSetCookie(
            Collection<String> values
    ) {
        for (String value : values) {
            if (value.startsWith("XSRF-TOKEN=")) {
                int start = "XSRF-TOKEN=".length();
                int end = value.indexOf(';', start);
                return end < 0
                        ? value.substring(start)
                        : value.substring(start, end);
            }
        }
        return null;
    }

    private static String present(String value) {
        return value == null || value.isBlank()
                ? "missing"
                : "present";
    }

    private static String fingerprint(String value) {
        if (value == null || value.isBlank()) return "-";

        try {
            byte[] digest = MessageDigest
                    .getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of()
                    .formatHex(digest)
                    .substring(0, 12);
        } catch (Exception ex) {
            return "hash-error";
        }
    }
}