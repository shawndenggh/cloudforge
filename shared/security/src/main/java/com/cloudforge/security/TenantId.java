package com.cloudforge.security;

import java.util.UUID;

public record TenantId(UUID value) {

    public TenantId {
        if (value == null) {
            throw new IllegalArgumentException("Tenant ID is required");
        }
    }

    public static TenantId parse(String value) {
        return new TenantId(UUID.fromString(value));
    }
}

