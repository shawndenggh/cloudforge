package com.cloudforge.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;

@AutoConfiguration
@ConditionalOnClass(Jwt.class)
public class TenantSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TenantJwtAuthenticationConverter tenantJwtAuthenticationConverter() {
        return new TenantJwtAuthenticationConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    CurrentTenant currentTenant() {
        return new SecurityContextCurrentTenant();
    }
}

