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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACCESS_COOKIE_NAME = "sakhtyar_access";
    public static final String REFRESH_COOKIE_NAME = "sakhtyar_refresh";

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

        if (token != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                var decoded = jwtService.verify(token);
                String username = decoded.getSubject();
                String sessionIdValue = decoded.getClaim("sid").asString();
                String userIdValue = decoded.getClaim("uid").asString();

                if (sessionIdValue == null || userIdValue == null) {
                    throw new IllegalArgumentException("Missing session claims.");
                }

                UUID sessionId = UUID.fromString(sessionIdValue);
                UUID userId = UUID.fromString(userIdValue);

                var session = sessionRepository.findById(sessionId)
                        .filter(value -> value.getUserId().equals(userId))
                        .filter(value -> value.isUsable())
                        .orElseThrow(() ->
                                new IllegalArgumentException("Session revoked.")
                        );

                UserDetails user =
                        userDetailsService.loadUserByUsername(username);

                if (user.isEnabled() && session.isUsable()) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user,
                                    null,
                                    user.getAuthorities()
                            );

                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                }
            } catch (
                    JWTVerificationException
                    | UsernameNotFoundException
                    | IllegalArgumentException ignored
            ) {
                // Continue unauthenticated.
            }
        }

        filterChain.doFilter(request, response);
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
}
