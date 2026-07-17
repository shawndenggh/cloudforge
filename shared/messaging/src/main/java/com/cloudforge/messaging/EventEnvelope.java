package com.cloudforge.messaging;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        EventScope scope,
        UUID tenantId,
        String source,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        String subject,
        Long subjectVersion,
        T payload) {

    public EventEnvelope {
        require(eventId, "eventId");
        requireText(eventType, "eventType");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
        require(scope, "scope");
        if (scope == EventScope.TENANT && tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for tenant-scoped events");
        }
        if (scope == EventScope.PLATFORM && tenantId != null) {
            throw new IllegalArgumentException("tenantId must be absent for platform-scoped events");
        }
        requireText(source, "source");
        require(occurredAt, "occurredAt");
        require(correlationId, "correlationId");
        require(payload, "payload");
        if ((subject == null) != (subjectVersion == null)) {
            throw new IllegalArgumentException("subject and subjectVersion must be provided together");
        }
        if (subjectVersion != null && subjectVersion < 1) {
            throw new IllegalArgumentException("subjectVersion must be positive");
        }
    }

    private static void require(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}

