package com.cloudforge.security;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class TenantJwtAuthenticationConverterTest {

    @Test
    void mapsPermissionCodesWithoutChangingTheirNames() {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("sub", "user-1", "permissions", List.of("order:read", "order:refund")));

        TenantJwtAuthenticationConverter converter = new TenantJwtAuthenticationConverter();

        assertThat(converter.authorities(jwt))
                .extracting(authority -> authority.getAuthority())
                .contains("order:read", "order:refund");
    }
}
