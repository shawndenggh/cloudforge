package com.cloudforge.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

final class SecurityContextCurrentTenant implements CurrentTenant {

    @Override
    public TenantId requireTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new MissingTenantException("An authenticated JWT is required");
        }

        String tenantId = jwt.getClaimAsString("tenant_id");
        if (tenantId == null || tenantId.isBlank()) {
            throw new MissingTenantException("The authenticated JWT has no tenant_id claim");
        }

        return TenantId.parse(tenantId);
    }
}

