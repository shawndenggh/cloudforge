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
package com.cloudforge.iam.identity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.cloudforge.iam.identity.IdentityModule.RegisterCommand;
import com.cloudforge.iam.identity.IdentityModule.UserProfile;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityModuleTests {

	private static final Instant REGISTERED_AT = Instant.parse("2026-07-24T04:00:00Z");

	private static final UUID USER_ID = UUID.fromString("018f0c24-7a00-7000-8000-000000000001");

	@Test
	void registersUserAndCreatesUniqueSessionIdentity() {
		IdentityModule module = new DefaultIdentityModule(new InMemoryIdentityStore(), password -> "argon2:" + password,
				new InMemoryIdentitySessionStore(), Clock.fixed(REGISTERED_AT, ZoneOffset.UTC));

		IdentityModule.Registration registration = module
			.register(new RegisterCommand("  New.User@Example.COM ", "correct horse battery staple"));
		IdentityModule.Registration anotherRegistration = module
			.register(new RegisterCommand("another@example.com", "another correct password"));

		assertThat(registration.user()).isEqualTo(new UserProfile(USER_ID, "new.user@example.com", REGISTERED_AT));
		assertThat(registration.sessionId()).isEqualTo("session-1");
		assertThat(anotherRegistration.sessionId()).isEqualTo("session-2").isNotEqualTo(registration.sessionId());
	}

	private static final class InMemoryIdentityStore implements IdentityStore {

		private final Map<UUID, UserProfile> users = new HashMap<>();

		@Override
		public UserProfile create(String email, String passwordHash, Instant registeredAt) {
			UserProfile user = new UserProfile(USER_ID, email, registeredAt);
			this.users.put(user.id(), user);
			return user;
		}

		@Override
		public Optional<UserProfile> findById(UUID userId) {
			return Optional.ofNullable(this.users.get(userId));
		}

	}

	private static final class InMemoryIdentitySessionStore implements IdentitySessionStore {

		private final AtomicInteger sequence = new AtomicInteger();

		@Override
		public String create(UUID userId) {
			return "session-" + this.sequence.incrementAndGet();
		}

	}

}
