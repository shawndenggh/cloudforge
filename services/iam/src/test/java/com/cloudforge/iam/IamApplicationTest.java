package com.cloudforge.iam;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IamApplicationTest {

    @Test
    void exposesAnApplicationEntryPoint() {
        assertThat(IamApplication.class).isNotNull();
    }
}

