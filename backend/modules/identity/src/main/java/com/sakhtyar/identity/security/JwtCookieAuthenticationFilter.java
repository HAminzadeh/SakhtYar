package com.sakhtyar.identity.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.sakhtyar.identity.application.DatabaseUserDetailsService;
import com.sakhtyar.identity.domain.AuthSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACCESS_COOKIE_NAME = "sakhtyar_access";
    public static final String REFRESH_COOKIE_NAME = "sakhtyar_refresh";
    public static final String AUTH_FAILURE_ATTRIBUTE = "sakhtyar.auth.failure";

    private static final Logger log =
            LoggerFactory.getLogger(JwtCookieAuthenticationFilter.class);

    private final JwtService jwtService;
    private final DatabaseUserDetailsService userDetailsService;
    private final AuthSessionRepository sessionRepository;

    public JwtCookieAuthenticationFilter(
            JwtService jwtService,
            DatabaseUserDetailsService userDetailsService,
            AuthSessionRepository sessionRepository
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.sessionRepository = sessionRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = extractBearer(request);

        if (token == null) {
            token = extractCookie(request, ACCESS_COOKIE_NAME);
        }

        if (token == null) {
            request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "ACCESS_TOKEN_MISSING");
            filterChain.doFilter(request, response);
            return;
        }

        var currentAuthentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (currentAuthentication != null
                && currentAuthentication.isAuthenticated()
                && !(currentAuthentication instanceof AnonymousAuthenticationToken)) {
            request.removeAttribute(AUTH_FAILURE_ATTRIBUTE);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            var decoded = jwtService.verify(token);

            String username = decoded.getSubject();
            String sessionIdValue = decoded.getClaim("sid").asString();
            String userIdValue = decoded.getClaim("uid").asString();

            if (username == null || username.isBlank()) {
                throw new AuthFailure("JWT_SUBJECT_MISSING");
            }
            if (sessionIdValue == null || userIdValue == null) {
                throw new AuthFailure("JWT_SESSION_CLAIMS_MISSING");
            }

            UUID sessionId = UUID.fromString(sessionIdValue);
            UUID userId = UUID.fromString(userIdValue);

            var session = sessionRepository.findById(sessionId)
                    .filter(value -> value.getUserId().equals(userId))
                    .orElseThrow(() -> new AuthFailure("AUTH_SESSION_NOT_FOUND"));

            if (!session.isUsable()) {
                throw new AuthFailure("AUTH_SESSION_EXPIRED_OR_REVOKED");
            }

            UserDetails user = userDetailsService.loadUserByUsername(username);

            if (!user.isEnabled()) {
                throw new AuthFailure("USER_DISABLED");
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            user.getAuthorities()
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.removeAttribute(AUTH_FAILURE_ATTRIBUTE);

        } catch (AuthFailure ex) {
            markFailure(request, ex.getMessage());
        } catch (JWTVerificationException ex) {
            markFailure(request, "JWT_INVALID_OR_EXPIRED");
        } catch (UsernameNotFoundException ex) {
            markFailure(request, "USER_NOT_FOUND");
        } catch (IllegalArgumentException ex) {
            markFailure(request, "JWT_CLAIM_INVALID");
        }

        filterChain.doFilter(request, response);
    }

    private void markFailure(
            HttpServletRequest request,
            String reason
    ) {
        request.setAttribute(AUTH_FAILURE_ATTRIBUTE, reason);

        log.warn(
                "Authentication rejected: reason={}, method={}, path={}",
                reason,
                request.getMethod(),
                request.getRequestURI()
        );
    }

    public static String extractCookie(
            HttpServletRequest request,
            String name
    ) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }

        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private static final class AuthFailure extends RuntimeException {
        private AuthFailure(String message) {
            super(message);
        }
    }
}