package com.sakhtyar.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Diagnostic metadata only. Never record usernames, tokens, cookies, headers,
 * query strings, CSRF fingerprints or user-provided URL components.
 */
public class SecurityDiagnosticsFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(SecurityDiagnosticsFilter.class);
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
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Object failure = request.getAttribute(JwtCookieAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE);
            // Only fixed, server-generated error codes may appear in diagnostic logs.
            String failureCode = failure instanceof String value
                    && value.matches("[A-Z_]{1,60}") ? value : "NONE";
            log.debug("Security boundary method={} path={} status={} authenticated={} failureCode={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    auth != null && auth.isAuthenticated(), failureCode);
        }
    }
}
