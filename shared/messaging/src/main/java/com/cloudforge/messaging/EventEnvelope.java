/*
 * Copyright 2026-present Shawn Deng and CloudForge contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudforge.messaging;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record EventEnvelope<T>(UUID eventId, String eventType, int eventVersion, EventScope scope,
		@Nullable UUID tenantId, String source, Instant occurredAt, UUID correlationId, @Nullable UUID causationId,
		@Nullable String subject, @Nullable Long subjectVersion, T payload) {

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

	private static void require(@Nullable Object value, String name) {
		if (value == null) {
			throw new IllegalArgumentException(name + " is required");
		}
	}

	private static void requireText(@Nullable String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
	}
}
