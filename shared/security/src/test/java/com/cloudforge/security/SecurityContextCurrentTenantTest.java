package com.cloudforge.security;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityContextCurrentTenantTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesTenantFromAuthenticatedJwt() {
        UUID tenantId = UUID.randomUUID();
        var jwt = TestJwts.withClaims(Map.of("sub", "user-1", "tenant_id", tenantId.toString()));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(new SecurityContextCurrentTenant().requireTenantId()).isEqualTo(new TenantId(tenantId));
    }

    @Test
    void rejectsAuthenticationWithoutTenant() {
        var jwt = TestJwts.withClaims(Map.of("sub", "user-1"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThatThrownBy(() -> new SecurityContextCurrentTenant().requireTenantId())
                .isInstanceOf(MissingTenantException.class);
    }

    private static final class TestJwts {

        private TestJwts() {
        }

        static org.springframework.security.oauth2.jwt.Jwt withClaims(Map<String, Object> claims) {
            return new org.springframework.security.oauth2.jwt.Jwt(
                    "token",
                    Instant.now(),
                    Instant.now().plusSeconds(300),
                    Map.of("alg", "none"),
                    claims);
        }
    }
}
