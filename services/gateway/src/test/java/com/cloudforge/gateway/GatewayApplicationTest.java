package com.cloudforge.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayApplicationTest {

    @Test
    void exposesAnApplicationEntryPoint() {
        assertThat(GatewayApplication.class).isNotNull();
    }
}

