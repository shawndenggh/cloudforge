package com.cloudforge.security;

/**
 * Resolves the Current Tenant from the authenticated execution context.
 */
public interface CurrentTenant {

    TenantId requireTenantId();
}

