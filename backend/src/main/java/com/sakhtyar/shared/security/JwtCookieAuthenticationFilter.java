package com.sakhtyar.shared.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.sakhtyar.identity.application.DatabaseUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "sakhtyar_access";

    private final JwtService jwtService;
    private final DatabaseUserDetailsService userDetailsService;

    public JwtCookieAuthenticationFilter(
            JwtService jwtService,
            DatabaseUserDetailsService userDetailsService
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = extractCookie(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String username = jwtService.verify(token).getSubject();
                UserDetails user = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                user.getAuthorities()
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JWTVerificationException ignored) {
                // Invalid or expired token -> continue unauthenticated.
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
