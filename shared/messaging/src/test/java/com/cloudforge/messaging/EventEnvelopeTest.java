package com.cloudforge.messaging;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEnvelopeTest {

    @Test
    void acceptsTenantScopedEventWithTenant() {
        UUID tenantId = UUID.randomUUID();

        EventEnvelope<String> envelope = envelope(EventScope.TENANT, tenantId, "order/42", 3L);

        assertThat(envelope.tenantId()).isEqualTo(tenantId);
        assertThat(envelope.subjectVersion()).isEqualTo(3L);
    }

    @Test
    void rejectsTenantScopedEventWithoutTenant() {
        assertThatThrownBy(() -> envelope(EventScope.TENANT, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void rejectsPartialOrderingMetadata() {
        assertThatThrownBy(() -> envelope(EventScope.PLATFORM, null, "order/42", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject and subjectVersion");
    }

    private EventEnvelope<String> envelope(
            EventScope scope, UUID tenantId, String subject, Long subjectVersion) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                "order.created",
                1,
                scope,
                tenantId,
                "order",
                Instant.now(),
                UUID.randomUUID(),
                null,
                subject,
                subjectVersion,
                "payload");
    }
}

