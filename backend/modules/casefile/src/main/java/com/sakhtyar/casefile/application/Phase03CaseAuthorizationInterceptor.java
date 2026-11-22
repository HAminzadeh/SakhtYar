package com.sakhtyar.casefile.application;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.server.ResponseStatusException;

/** Defence in depth: enforce case authorization on nested API paths before controller invocation. */
@Component
public final class Phase03CaseAuthorizationInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private static final String PREFIX = "/api/v1/cases/";
    private final CaseAccessService access;

    public Phase03CaseAuthorizationInterceptor(CaseAccessService access) {
        this.access = access;
    }

    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/v1/cases/**");
    }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) return true;
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) uri = uri.substring(context.length());
        if (!uri.startsWith(PREFIX)) return true; // POST /cases creates a new case
        String tail = uri.substring(PREFIX.length());
        if (tail.isEmpty()) return true;
        String segment = tail.split("/", 2)[0];
        UUID caseId;
        try { caseId = UUID.fromString(segment); }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found.");
        }
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) access.requireRead(caseId);
        else access.requireWrite(caseId);
        return true;
    }
}
