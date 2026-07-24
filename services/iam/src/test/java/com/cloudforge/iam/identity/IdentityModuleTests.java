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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import com.cloudforge.iam.identity.IdentityModule.RegisterCommand;
import com.cloudforge.iam.identity.IdentityModule.LoginCommand;
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

	private static final String DUMMY_PASSWORD_HASH = "argon2:dummy password phrase";

	@Test
	void registersUserAndCreatesUniqueSessionIdentity() {
		IdentityModule module = new DefaultIdentityModule(new InMemoryIdentityStore(), testPasswordHasher(),
				new InMemoryIdentitySessionStore(), noRateLimit(), Clock.fixed(REGISTERED_AT, ZoneOffset.UTC),
				DUMMY_PASSWORD_HASH);

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
				new InMemoryIdentitySessionStore(), noRateLimit(), Clock.fixed(REGISTERED_AT, ZoneOffset.UTC),
				DUMMY_PASSWORD_HASH);

		module.register(new RegisterCommand("valid@example.com", password, confirmation));

		assertThat(passwordHasher.passwords()).containsExactly(expectedNormalizedPassword);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidPasswords")
	void rejectsInvalidPasswordPolicyBoundaries(String description, String password, String confirmation,
			String expectedField, String expectedCode) {
		RecordingPasswordHasher passwordHasher = new RecordingPasswordHasher();
		IdentityModule module = new DefaultIdentityModule(new InMemoryIdentityStore(), passwordHasher,
				new InMemoryIdentitySessionStore(), noRateLimit(), Clock.fixed(REGISTERED_AT, ZoneOffset.UTC),
				DUMMY_PASSWORD_HASH);

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
				new InMemoryIdentitySessionStore(), noRateLimit(), Clock.fixed(REGISTERED_AT, ZoneOffset.UTC),
				DUMMY_PASSWORD_HASH);

		IdentityModule.Registration registration = module.register(
				new RegisterCommand(maximumEmail, "correct horse battery staple", "correct horse battery staple"));

		assertThat(registration.user().email()).hasSize(254).isEqualTo(maximumEmail);
		assertThatThrownBy(() -> module.register(new RegisterCommand(maximumEmail + "e", "correct horse battery staple",
				"correct horse battery staple")))
			.isInstanceOf(IdentityValidationException.class)
			.satisfies(exception -> assertThat(((IdentityValidationException) exception).errors())
				.containsExactly(new IdentityValidationException.FieldError("email", "IAM_EMAIL_INVALID")));
	}

	@Test
	void limitsRegistrationSourceBurstAndHourlyRateWithRetryAfter() {
		MutableClock clock = new MutableClock(REGISTERED_AT);
		IdentityModule module = new DefaultIdentityModule(new InMemoryIdentityStore(), testPasswordHasher(),
				new InMemoryIdentitySessionStore(),
				new DefaultRegistrationRateLimiter(new InMemoryRegistrationRateLimitStore(), clock), clock,
				DUMMY_PASSWORD_HASH);

		for (int attempt = 0; attempt < 5; attempt++) {
			module.checkRegistrationSource("192.0.2.10");
		}
		assertThatThrownBy(() -> module.checkRegistrationSource("192.0.2.10"))
			.isInstanceOf(RegistrationRateLimitedException.class)
			.satisfies(exception -> assertThat(((RegistrationRateLimitedException) exception).retryAfter())
				.isEqualTo(Duration.ofSeconds(12)));

		for (int attempt = 0; attempt < 16; attempt++) {
			clock.advance(Duration.ofSeconds(12));
			module.checkRegistrationSource("192.0.2.10");
		}
		clock.advance(Duration.ofSeconds(12));
		assertThatThrownBy(() -> module.checkRegistrationSource("192.0.2.10"))
			.isInstanceOf(RegistrationRateLimitedException.class)
			.satisfies(exception -> assertThat(((RegistrationRateLimitedException) exception).retryAfter())
				.isEqualTo(Duration.ofSeconds(156)));
	}

	@Test
	void limitsNormalizedEmailBeforeAdditionalPasswordHashesOrUserWrites() {
		MutableClock clock = new MutableClock(REGISTERED_AT);
		RecordingPasswordHasher passwordHasher = new RecordingPasswordHasher();
		InMemoryIdentityStore identities = new InMemoryIdentityStore();
		IdentityModule module = new DefaultIdentityModule(identities, passwordHasher,
				new InMemoryIdentitySessionStore(),
				new DefaultRegistrationRateLimiter(new InMemoryRegistrationRateLimitStore(), clock), clock,
				DUMMY_PASSWORD_HASH);
		RegisterCommand command = new RegisterCommand(" Rate.Limited@Example.COM ", "correct horse battery staple",
				"correct horse battery staple");

		for (int attempt = 0; attempt < 10; attempt++) {
			module.checkRegistrationEmail(command.email());
			module.register(command);
		}

		assertThatThrownBy(() -> module.checkRegistrationEmail(command.email()))
			.isInstanceOf(RegistrationRateLimitedException.class)
			.satisfies(exception -> assertThat(((RegistrationRateLimitedException) exception).retryAfter())
				.isEqualTo(Duration.ofMinutes(6)));
		assertThat(passwordHasher.passwords()).hasSize(10);
		assertThat(identities.creates()).isEqualTo(10);
	}

	@Test
	void unavailableRateLimitStateFailsBeforePasswordValidationHashAndPersistence() {
		RecordingPasswordHasher passwordHasher = new RecordingPasswordHasher();
		InMemoryIdentityStore identities = new InMemoryIdentityStore();
		Clock clock = Clock.fixed(REGISTERED_AT, ZoneOffset.UTC);
		IdentityModule module = new DefaultIdentityModule(identities, passwordHasher,
				new InMemoryIdentitySessionStore(), new DefaultRegistrationRateLimiter((buckets, now) -> {
					throw new RegistrationRateLimitUnavailableException();
				}, clock), clock, DUMMY_PASSWORD_HASH);

		assertThatThrownBy(() -> module.checkRegistrationEmail("valid@example.com"))
			.isInstanceOf(RegistrationRateLimitUnavailableException.class);
		assertThat(passwordHasher.passwords()).isEmpty();
		assertThat(identities.creates()).isZero();
	}

	@Test
	void retainsCommittedUserAfterBoundedSessionCreationFailures() {
		InMemoryIdentityStore identities = new InMemoryIdentityStore();
		AlwaysFailingIdentitySessionStore sessions = new AlwaysFailingIdentitySessionStore(
				() -> identities.creates() == 1);
		IdentityModule module = new DefaultIdentityModule(identities, testPasswordHasher(), sessions, noRateLimit(),
				Clock.fixed(REGISTERED_AT, ZoneOffset.UTC), DUMMY_PASSWORD_HASH);

		assertThatThrownBy(() -> module.register(new RegisterCommand("recoverable@example.com",
				"correct horse battery staple", "correct horse battery staple")))
			.isInstanceOf(RegistrationSessionUnavailableException.class);

		assertThat(sessions.attempts()).isEqualTo(3);
		assertThat(identities.creates()).isEqualTo(1);
		assertThat(identities.findById(USER_ID))
			.contains(new UserProfile(USER_ID, "recoverable@example.com", REGISTERED_AT));
		assertThat(identities.passwordHashes()).containsExactly("argon2:correct horse battery staple");
	}

	@Test
	void returnsRegistrationWhenSessionCreationRecoversWithinRetryBoundary() {
		RecoveringIdentitySessionStore sessions = new RecoveringIdentitySessionStore();
		IdentityModule module = new DefaultIdentityModule(new InMemoryIdentityStore(), testPasswordHasher(), sessions,
				noRateLimit(), Clock.fixed(REGISTERED_AT, ZoneOffset.UTC), DUMMY_PASSWORD_HASH);

		IdentityModule.Registration registration = module.register(new RegisterCommand("retry@example.com",
				"correct horse battery staple", "correct horse battery staple"));

		assertThat(registration.sessionId()).isEqualTo("recovered-session");
		assertThat(sessions.attempts()).isEqualTo(3);
	}

	@Test
	void logsInWithNormalizedEmailAndCreatesANewSessionForEverySuccess() {
		String normalizedPassword = "caf\u00e9 password phrase";
		String decomposedPassword = "cafe\u0301 password phrase";
		InMemoryIdentityStore identities = new InMemoryIdentityStore();
		identities.create("login@example.com", "argon2:" + normalizedPassword, REGISTERED_AT);
		RecordingPasswordHasher passwordHasher = new RecordingPasswordHasher();
		IdentityModule module = new DefaultIdentityModule(identities, passwordHasher,
				new InMemoryIdentitySessionStore(), noRateLimit(), Clock.fixed(REGISTERED_AT, ZoneOffset.UTC),
				DUMMY_PASSWORD_HASH);

		IdentityModule.Authentication first = module
			.login(new LoginCommand("  Login@Example.COM ", decomposedPassword));
		IdentityModule.Authentication second = module.login(new LoginCommand("login@example.com", normalizedPassword));

		assertThat(first.sessionId()).isEqualTo("session-1");
		assertThat(second.sessionId()).isEqualTo("session-2").isNotEqualTo(first.sessionId());
		assertThat(passwordHasher.verifications()).containsExactly(
				new PasswordVerification(normalizedPassword, "argon2:" + normalizedPassword),
				new PasswordVerification(normalizedPassword, "argon2:" + normalizedPassword));
	}

	@Test
	void wrongPasswordAndUnknownEmailUseTheSameFailureAndPasswordVerificationPath() {
		InMemoryIdentityStore identities = new InMemoryIdentityStore();
		identities.create("known@example.com", "argon2:correct password phrase", REGISTERED_AT);
		RecordingPasswordHasher passwordHasher = new RecordingPasswordHasher();
		IdentityModule module = new DefaultIdentityModule(identities, passwordHasher,
				new InMemoryIdentitySessionStore(), noRateLimit(), Clock.fixed(REGISTERED_AT, ZoneOffset.UTC),
				DUMMY_PASSWORD_HASH);

		assertThatThrownBy(() -> module.login(new LoginCommand("known@example.com", "wrong password phrase")))
			.isInstanceOf(InvalidCredentialsException.class);
		assertThatThrownBy(() -> module.login(new LoginCommand("unknown@example.com", "wrong password phrase")))
			.isInstanceOf(InvalidCredentialsException.class);

		assertThat(passwordHasher.verifications()).containsExactly(
				new PasswordVerification("wrong password phrase", "argon2:correct password phrase"),
				new PasswordVerification("wrong password phrase", "argon2:dummy password phrase"));
	}

	private static RegistrationRateLimiter noRateLimit() {
		return new RegistrationRateLimiter() {
			@Override
			public void checkSource(String clientIp) {
			}

			@Override
			public void checkEmail(String normalizedEmail) {
			}
		};
	}

	private static PasswordHasher testPasswordHasher() {
		return new PasswordHasher() {
			@Override
			public String hash(String password) {
				return "argon2:" + password;
			}

			@Override
			public boolean matches(String password, String passwordHash) {
				return passwordHash.equals("argon2:" + password);
			}
		};
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

		private final java.util.ArrayList<PasswordVerification> verifications = new java.util.ArrayList<>();

		@Override
		public String hash(String password) {
			this.passwords.add(password);
			return "argon2:" + password;
		}

		@Override
		public boolean matches(String password, String passwordHash) {
			this.verifications.add(new PasswordVerification(password, passwordHash));
			return passwordHash.equals("argon2:" + password);
		}

		List<String> passwords() {
			return List.copyOf(this.passwords);
		}

		List<PasswordVerification> verifications() {
			return List.copyOf(this.verifications);
		}

	}

	private static final class InMemoryIdentityStore implements IdentityStore {

		private final Map<UUID, UserProfile> users = new HashMap<>();

		private final AtomicInteger creates = new AtomicInteger();

		private final java.util.ArrayList<String> passwordHashes = new java.util.ArrayList<>();

		@Override
		public UserProfile create(String email, String passwordHash, Instant registeredAt) {
			this.creates.incrementAndGet();
			this.passwordHashes.add(passwordHash);
			UserProfile user = new UserProfile(USER_ID, email, registeredAt);
			this.users.put(user.id(), user);
			return user;
		}

		@Override
		public Optional<UserProfile> findById(UUID userId) {
			return Optional.ofNullable(this.users.get(userId));
		}

		@Override
		public Optional<PasswordCredential> findCredentialByEmail(String email) {
			return this.users.values()
				.stream()
				.filter(user -> user.email().equals(email))
				.findFirst()
				.map(user -> new PasswordCredential(user.id(), this.passwordHashes.getFirst()));
		}

		int creates() {
			return this.creates.get();
		}

		List<String> passwordHashes() {
			return List.copyOf(this.passwordHashes);
		}

	}

	private static final class InMemoryIdentitySessionStore implements IdentitySessionStore {

		private final AtomicInteger sequence = new AtomicInteger();

		@Override
		public String create(UUID userId) {
			return "session-" + this.sequence.incrementAndGet();
		}

	}

	private static final class AlwaysFailingIdentitySessionStore implements IdentitySessionStore {

		private final BooleanSupplier userCommitted;

		private final AtomicInteger attempts = new AtomicInteger();

		AlwaysFailingIdentitySessionStore(BooleanSupplier userCommitted) {
			this.userCommitted = userCommitted;
		}

		@Override
		public String create(UUID userId) {
			assertThat(this.userCommitted.getAsBoolean()).isTrue();
			this.attempts.incrementAndGet();
			throw new IdentitySessionUnavailableException();
		}

		int attempts() {
			return this.attempts.get();
		}

	}

	private static final class RecoveringIdentitySessionStore implements IdentitySessionStore {

		private final AtomicInteger attempts = new AtomicInteger();

		@Override
		public String create(UUID userId) {
			if (this.attempts.incrementAndGet() < 3) {
				throw new IdentitySessionUnavailableException();
			}
			return "recovered-session";
		}

		int attempts() {
			return this.attempts.get();
		}

	}

	private static final class InMemoryRegistrationRateLimitStore implements RegistrationRateLimitStore {

		private final Map<String, BucketState> states = new HashMap<>();

		@Override
		public Duration consume(List<TokenBucket> buckets, Instant now) {
			Map<TokenBucket, Long> availableTokenUnits = new HashMap<>();
			Duration retryAfter = Duration.ZERO;
			for (TokenBucket bucket : buckets) {
				long refillIntervalMillis = bucket.refillInterval().toMillis();
				long capacityUnits = bucket.capacity() * refillIntervalMillis;
				BucketState state = this.states.computeIfAbsent(bucket.key(),
						key -> new BucketState(capacityUnits, now));
				long elapsed = Duration.between(state.updatedAt(), now).toMillis();
				long availableUnits = Math.min(capacityUnits, state.tokenUnits() + elapsed);
				availableTokenUnits.put(bucket, availableUnits);
				if (availableUnits < refillIntervalMillis) {
					Duration wait = Duration.ofMillis(refillIntervalMillis - availableUnits);
					if (wait.compareTo(retryAfter) > 0) {
						retryAfter = wait;
					}
				}
			}
			boolean allowed = retryAfter.isZero();
			availableTokenUnits.forEach((bucket, availableUnits) -> this.states.put(bucket.key(), new BucketState(
					allowed ? availableUnits - bucket.refillInterval().toMillis() : availableUnits, now)));
			return retryAfter;
		}

	}

	private record BucketState(long tokenUnits, Instant updatedAt) {
	}

	private record PasswordVerification(String password, String passwordHash) {
	}

	private static final class MutableClock extends Clock {

		private Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		void advance(Duration duration) {
			this.instant = this.instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.instant;
		}

	}

}
