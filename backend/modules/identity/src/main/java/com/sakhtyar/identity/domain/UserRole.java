package com.sakhtyar.identity.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum UserRole {
    ADMIN(true),

    PROJECT_MANAGER(
            false,
            Permission.CASE_READ,
            Permission.CASE_WRITE,
            Permission.PROPERTY_READ,
            Permission.PROPERTY_WRITE,
            Permission.OWNER_READ,
            Permission.OWNER_WRITE,
            Permission.DOCUMENT_READ,
            Permission.DOCUMENT_WRITE,
            Permission.AGENT_USE,
            Permission.GLOSSARY_MANAGE
    ),

    ANALYST(
            false,
            Permission.CASE_READ,
            Permission.PROPERTY_READ,
            Permission.PROPERTY_WRITE,
            Permission.OWNER_READ,
            Permission.DOCUMENT_READ,
            Permission.AGENT_USE
    ),

    LEGAL_EXPERT(
            false,
            Permission.CASE_READ,
            Permission.PROPERTY_READ,
            Permission.OWNER_READ,
            Permission.DOCUMENT_READ,
            Permission.DOCUMENT_WRITE,
            Permission.AGENT_USE
    ),

    READ_ONLY(
            false,
            Permission.CASE_READ,
            Permission.PROPERTY_READ,
            Permission.OWNER_READ,
            Permission.DOCUMENT_READ
    );

    private final Set<Permission> permissions;

    UserRole(boolean all, Permission... permissions) {
        EnumSet<Permission> values = all
                ? EnumSet.allOf(Permission.class)
                : EnumSet.noneOf(Permission.class);

        if (!all) {
            Collections.addAll(values, permissions);
        }

        this.permissions = Collections.unmodifiableSet(values);
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }
}
