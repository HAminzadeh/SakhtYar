package com.sakhtyar.globalization.application;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/** Defense in depth for service and background entry points, not only MVC routes. */
public final class GlobalizationAccess {
    private GlobalizationAccess() { }
    public static void requireAdmin() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a.getAuthorities() == null ||
                a.getAuthorities().stream().anyMatch(x -> "ROLE_TESTER".equals(x.getAuthority())) ||
                a.getAuthorities().stream().noneMatch(x -> "USER_MANAGE".equals(x.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Master-data administration is forbidden.");
    }
}
