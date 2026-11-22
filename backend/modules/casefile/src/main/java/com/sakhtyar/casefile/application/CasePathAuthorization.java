package com.sakhtyar.casefile.application;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Protect case-scoped MVC endpoints, including owners, property, document,
 * and the web BFF summary, before controller logic accesses case resources.
 * Endpoints addressed solely by a document/property/analysis id need their
 * own resource-to-case guard and are listed in the handoff risk register.
 */
@Configuration
public class CasePathAuthorization implements WebMvcConfigurer {
    private static final Pattern SCOPED_CASE = Pattern.compile(
        "^/api/(?:v1|web/v1)/cases/([0-9a-fA-F-]{36})(?:/.*)?$");
    private final CaseAccessService access;

    public CasePathAuthorization(CaseAccessService access) { this.access = access; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
                Matcher matcher = SCOPED_CASE.matcher(request.getServletPath());
                if (matcher.matches()) {
                    try {
                        access.requireRead(UUID.fromString(matcher.group(1)));
                    } catch (IllegalArgumentException invalidId) {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        return false;
                    }
                }
                return true;
            }
        });
    }
}
