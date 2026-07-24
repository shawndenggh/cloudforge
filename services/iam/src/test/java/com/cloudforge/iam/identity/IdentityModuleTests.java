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
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.cloudforge.iam.identity.IdentityModule.RegisterCommand;
import com.cloudforge.iam.identity.IdentityModule.UserProfile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityModuleTests {

	private static final Instant REGISTERED_AT = Instant.parse("2026-07-24T04:00:00Z");

	private static final UUID USER_ID = UUID.fromString("018f0c24-7a00-7000-8000-000000000001");

	@Test
	void registersUserAndCreatesUniqueSessionIdentity() {
		IdentityModule module = new DefaultIdentityModule(new InMemoryIdentityStore(), password -> "argon2:" + password,
				new InMemoryIdentitySessionStore(), Clock.fixed(REGISTERED_AT, ZoneOffset.UTC));

		IdentityModule.Registration registration = module.register(new RegisterCommand("  New.User@Example.COM ",
				"correct horse battery staple", "correct horse battery staple"));
		IdentityModule.Registration anotherRegistration = module.register(
				new RegisterCommand("another@example.com", "another correct password", "another correct password"));

		assertThat(registration.user()).isEqualTo(new UserProfile(USER_ID, "new.user@example.com", REGISTERED_AT));
		assertThat(registration.sessionId()).isEqualTo("session-1");
		assertThat(anotherRegistration.sessionId()).isEqualTo("session-2").isNotEqualTo(registration.sessionId());
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("validPasswords")
	void acceptsPasswordPolicyBoundaries(String description, String password, String confirmation,
			String expectedNormalizedPassword) {
		RecordingPasswordHasher passwordHasher = new RecordingPasswordHasher();
		IdentityModule module = new DefaultIdentityModule(new InMemoryIdentityStore(), passwordHasher,
				new InMemoryIdentitySessionStore(), Clock.fixed(REGISTERED_AT, ZoneOffset.UTC));

		module.register(new RegisterCommand("valid@example.com", password, confirmation));

		assertThat(passwordHasher.passwords()).containsExactly(expectedNormalizedPassword);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidPasswords")
	void rejectsInvalidPasswordPolicyBoundaries(String description, String password, String confirmation,
			String expectedField, String expectedCode) {
		RecordingPasswordHasher passwordHasher = new RecordingPasswordHasher();
		IdentityModule module = new DefaultIdentityModule(new InMemoryIdentityStore(), passwordHasher,
				new InMemoryIdentitySessionStore(), Clock.fixed(REGISTERED_AT, ZoneOffset.UTC));

		assertThatThrownBy(() -> module.register(new RegisterCommand("valid@example.com", password, confirmation)))
			.isInstanceOf(IdentityValidationException.class)
			.satisfies(exception -> assertThat(((IdentityValidationException) exception).errors())
				.containsExactly(new IdentityValidationException.FieldError(expectedField, expectedCode)));
		assertThat(passwordHasher.passwords()).isEmpty();
	}

	@Test
	void acceptsMaximumEmailLengthWithoutTruncationAndRejectsLongerInput() {
		String maximumEmail = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
		RecordingPasswordHasher passwordHasher = new RecordingPasswordHasher();
		IdentityModule module = new DefaultIdentityModule(new InMemoryIdentityStore(), passwordHasher,
				new InMemoryIdentitySessionStore(), Clock.fixed(REGISTERED_AT, ZoneOffset.UTC));

		IdentityModule.Registration registration = module.register(
				new RegisterCommand(maximumEmail, "correct horse battery staple", "correct horse battery staple"));

		assertThat(registration.user().email()).hasSize(254).isEqualTo(maximumEmail);
		assertThatThrownBy(() -> module.register(new RegisterCommand(maximumEmail + "e", "correct horse battery staple",
				"correct horse battery staple")))
			.isInstanceOf(IdentityValidationException.class)
			.satisfies(exception -> assertThat(((IdentityValidationException) exception).errors())
				.containsExactly(new IdentityValidationException.FieldError("email", "IAM_EMAIL_INVALID")));
	}

	private static List<Arguments> validPasswords() {
		String composed = "caf\u00e9 password phrase";
		String decomposed = "cafe\u0301 password phrase";
		return List.of(Arguments.of("15 code points", "123456789012345", "123456789012345", "123456789012345"),
				Arguments.of("128 code points", "a".repeat(128), "a".repeat(128), "a".repeat(128)),
				Arguments.of("spaces are preserved", "  password phrase  ", "  password phrase  ",
						"  password phrase  "),
				Arguments.of("NFD and NFC compare after normalization", decomposed, composed, composed));
	}

	private static List<Arguments> invalidPasswords() {
		return List.of(
				Arguments.of("14 code points", "a".repeat(14), "a".repeat(14), "password",
						"IAM_PASSWORD_LENGTH_INVALID"),
				Arguments.of("129 code points", "a".repeat(129), "a".repeat(129), "password",
						"IAM_PASSWORD_LENGTH_INVALID"),
				Arguments.of("control character", "valid password\u0000phrase", "valid password\u0000phrase",
						"password", "IAM_PASSWORD_CONTROL_CHARACTER"),
				Arguments.of("confirmation mismatch", "correct password phrase", "different password phrase",
						"confirmPassword", "IAM_PASSWORD_CONFIRMATION_MISMATCH"));
	}

	private static final class RecordingPasswordHasher implements PasswordHasher {

		private final java.util.ArrayList<String> passwords = new java.util.ArrayList<>();

		@Override
		public String hash(String password) {
			this.passwords.add(password);
			return "argon2:" + password;
		}

		List<String> passwords() {
			return List.copyOf(this.passwords);
		}

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
