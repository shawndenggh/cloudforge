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

import java.text.Normalizer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.cloudforge.iam.identity.IdentityValidationException.FieldError;
import com.cloudforge.iam.identity.IdentityStore.PasswordCredential;

public final class DefaultIdentityModule implements IdentityModule {

	private static final int SESSION_CREATION_ATTEMPTS = 3;

	private static final LoginRateLimiter NO_LOGIN_RATE_LIMIT = new LoginRateLimiter() {
		@Override
		public void check(String normalizedEmail, String clientIp) {
		}

		@Override
		public void recordFailure(String normalizedEmail, String clientIp) {
		}

		@Override
		public void recordSuccess(String normalizedEmail) {
		}
	};

	private final IdentityStore identityStore;

	private final PasswordHasher passwordHasher;

	private final IdentitySessionStore sessionStore;

	private final RegistrationRateLimiter registrationRateLimiter;

	private final Clock clock;

	private final String dummyPasswordHash;

	private final PasswordHashUpgradeFailureRecorder passwordHashUpgradeFailures;

	private final LoginRateLimiter loginRateLimiter;

	public DefaultIdentityModule(IdentityStore identityStore, PasswordHasher passwordHasher,
			IdentitySessionStore sessionStore, RegistrationRateLimiter registrationRateLimiter, Clock clock,
			String dummyPasswordHash) {
		this(identityStore, passwordHasher, sessionStore, registrationRateLimiter, clock, dummyPasswordHash, () -> {
		});
	}

	public DefaultIdentityModule(IdentityStore identityStore, PasswordHasher passwordHasher,
			IdentitySessionStore sessionStore, RegistrationRateLimiter registrationRateLimiter, Clock clock,
			String dummyPasswordHash, PasswordHashUpgradeFailureRecorder passwordHashUpgradeFailures) {
		this(identityStore, passwordHasher, sessionStore, registrationRateLimiter, clock, dummyPasswordHash,
				passwordHashUpgradeFailures, NO_LOGIN_RATE_LIMIT);
	}

	public DefaultIdentityModule(IdentityStore identityStore, PasswordHasher passwordHasher,
			IdentitySessionStore sessionStore, RegistrationRateLimiter registrationRateLimiter, Clock clock,
			String dummyPasswordHash, PasswordHashUpgradeFailureRecorder passwordHashUpgradeFailures,
			LoginRateLimiter loginRateLimiter) {
		this.identityStore = identityStore;
		this.passwordHasher = passwordHasher;
		this.sessionStore = sessionStore;
		this.registrationRateLimiter = registrationRateLimiter;
		this.clock = clock;
		this.dummyPasswordHash = dummyPasswordHash;
		this.passwordHashUpgradeFailures = passwordHashUpgradeFailures;
		this.loginRateLimiter = loginRateLimiter;
	}

	@Override
	public Registration register(RegisterCommand command) {
		String email = normalizeEmail(command.email());
		String password = Normalizer.normalize(command.password(), Normalizer.Form.NFC);
		String confirmPassword = Normalizer.normalize(command.confirmPassword(), Normalizer.Form.NFC);
		List<FieldError> errors = new ArrayList<>();
		if (!isValidEmailInput(command.email(), email)) {
			errors.add(new FieldError("email", "IAM_EMAIL_INVALID"));
		}
		errors.addAll(validatePassword(password, confirmPassword));
		if (!errors.isEmpty()) {
			throw new IdentityValidationException(errors);
		}
		UserProfile user = this.identityStore.create(email, this.passwordHasher.hash(password), this.clock.instant());
		return new Registration(user, createSession(user.id(), true));
	}

	@Override
	public Authentication login(LoginCommand command) {
		String email = normalizeEmail(command.email());
		String password = Normalizer.normalize(command.password(), Normalizer.Form.NFC);
		List<FieldError> errors = new ArrayList<>();
		if (!isValidEmailInput(command.email(), email)) {
			errors.add(new FieldError("email", "IAM_EMAIL_INVALID"));
		}
		if (password.codePointCount(0, password.length()) > 128) {
			errors.add(new FieldError("password", "IAM_PASSWORD_LENGTH_INVALID"));
		}
		if (!errors.isEmpty()) {
			throw new IdentityValidationException(errors);
		}

		this.loginRateLimiter.check(email, command.clientIp());
		Optional<PasswordCredential> credential = this.identityStore.findCredentialByEmail(email);
		String passwordHash = credential.map(PasswordCredential::passwordHash).orElse(this.dummyPasswordHash);
		boolean matches = this.passwordHasher.matches(password, passwordHash);
		if (credential.isEmpty() || !matches) {
			this.loginRateLimiter.recordFailure(email, command.clientIp());
			throw new InvalidCredentialsException();
		}
		PasswordCredential verifiedCredential = credential.orElseThrow();
		this.loginRateLimiter.recordSuccess(email);
		upgradePasswordHash(password, verifiedCredential);
		return new Authentication(createSession(verifiedCredential.userId(), false));
	}

	@Override
	public void logout(String sessionId) {
		this.sessionStore.revoke(sessionId);
	}

	@Override
	public void checkRegistrationSource(String clientIp) {
		this.registrationRateLimiter.checkSource(clientIp);
	}

	@Override
	public void checkRegistrationEmail(String email) {
		this.registrationRateLimiter.checkEmail(normalizeEmail(email));
	}

	@Override
	public Optional<UserProfile> findUser(UUID userId) {
		return this.identityStore.findById(userId);
	}

	private String createSession(UUID userId, boolean registration) {
		for (int attempt = 1; attempt <= SESSION_CREATION_ATTEMPTS; attempt++) {
			try {
				return this.sessionStore.create(userId);
			}
			catch (IdentitySessionUnavailableException exception) {
				if (attempt == SESSION_CREATION_ATTEMPTS) {
					if (registration) {
						throw new RegistrationSessionUnavailableException();
					}
					throw new LoginSessionUnavailableException();
				}
			}
		}
		throw new AssertionError("Session retry loop exhausted without a result");
	}

	private void upgradePasswordHash(String password, PasswordCredential credential) {
		if (!this.passwordHasher.upgradeEncoding(credential.passwordHash())) {
			return;
		}
		String upgradedPasswordHash = this.passwordHasher.hash(password);
		try {
			this.identityStore.updatePasswordHash(credential.userId(), upgradedPasswordHash);
		}
		catch (RuntimeException exception) {
			this.passwordHashUpgradeFailures.record();
		}
	}

	private static List<FieldError> validatePassword(String password, String confirmPassword) {
		List<FieldError> errors = new ArrayList<>();
		int codePointCount = password.codePointCount(0, password.length());
		if (codePointCount < 15 || codePointCount > 128) {
			errors.add(new FieldError("password", "IAM_PASSWORD_LENGTH_INVALID"));
		}
		if (password.codePoints().anyMatch(codePoint -> Character.getType(codePoint) == Character.CONTROL)) {
			errors.add(new FieldError("password", "IAM_PASSWORD_CONTROL_CHARACTER"));
		}
		if (!password.equals(confirmPassword)) {
			errors.add(new FieldError("confirmPassword", "IAM_PASSWORD_CONFIRMATION_MISMATCH"));
		}
		return errors;
	}

	private static String normalizeEmail(String email) {
		return Normalizer.normalize(email.trim(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
	}

	private static boolean isValidEmailInput(String input, String normalized) {
		if (input.codePointCount(0, input.length()) > 254 || normalized.length() > 254
				|| normalized.chars().anyMatch(character -> character > 0x7f)) {
			return false;
		}

		int at = normalized.indexOf('@');
		if (at < 1 || at != normalized.lastIndexOf('@') || at > 64 || at == normalized.length() - 1) {
			return false;
		}

		String localPart = normalized.substring(0, at);
		String domain = normalized.substring(at + 1);
		if (domain.length() > 253 || localPart.startsWith(".") || localPart.endsWith(".") || localPart.contains("..")
				|| !localPart.chars().allMatch(DefaultIdentityModule::isLocalPartCharacter)) {
			return false;
		}

		for (String label : domain.split("\\.", -1)) {
			if (label.isEmpty() || label.length() > 63 || !isAsciiLetterOrDigit(label.charAt(0))
					|| !isAsciiLetterOrDigit(label.charAt(label.length() - 1))
					|| !label.chars().allMatch(character -> isAsciiLetterOrDigit(character) || character == '-')) {
				return false;
			}
		}
		return true;
	}

	private static boolean isLocalPartCharacter(int character) {
		return isAsciiLetterOrDigit(character) || "!#$%&'*+-/=?^_`{|}~.".indexOf(character) >= 0;
	}

	private static boolean isAsciiLetterOrDigit(int character) {
		return (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9');
	}

}
