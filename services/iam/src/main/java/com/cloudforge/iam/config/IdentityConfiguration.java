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
package com.cloudforge.iam.config;

import java.time.Clock;
import java.util.Objects;

import com.cloudforge.iam.identity.DefaultIdentityModule;
import com.cloudforge.iam.identity.DefaultLoginRateLimiter;
import com.cloudforge.iam.identity.DefaultRegistrationRateLimiter;
import com.cloudforge.iam.identity.IdentityModule;
import com.cloudforge.iam.identity.IdentitySessionStore;
import com.cloudforge.iam.identity.IdentityStore;
import com.cloudforge.iam.identity.LoginRateLimiter;
import com.cloudforge.iam.identity.LoginRateLimitStore;
import com.cloudforge.iam.identity.PasswordHashUpgradeFailureRecorder;
import com.cloudforge.iam.identity.PasswordHasher;
import com.cloudforge.iam.identity.RegistrationRateLimiter;
import com.cloudforge.iam.identity.RegistrationRateLimitStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import com.password4j.Argon2Function;
import com.password4j.types.Argon2;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
class IdentityConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		Argon2Function function = Argon2Function.getInstance(19_456, 2, 1, 32, Argon2.ID);
		return new CloudForgeArgon2PasswordEncoder(function);
	}

	@Bean
	PasswordHasher passwordHasher(PasswordEncoder passwordEncoder) {
		return new PasswordHasher() {
			@Override
			public String hash(String password) {
				return Objects.requireNonNull(passwordEncoder.encode(password));
			}

			@Override
			public boolean matches(String password, String passwordHash) {
				return passwordEncoder.matches(password, passwordHash);
			}

			@Override
			public boolean upgradeEncoding(String passwordHash) {
				return passwordEncoder.upgradeEncoding(passwordHash);
			}
		};
	}

	@Bean
	IdentityModule identityModule(IdentityStore identityStore, PasswordHasher passwordHasher,
			IdentitySessionStore sessionStore, RegistrationRateLimiter registrationRateLimiter, Clock clock,
			PasswordHashUpgradeFailureRecorder passwordHashUpgradeFailures, LoginRateLimiter loginRateLimiter) {
		String dummyPasswordHash = passwordHasher.hash("CloudForge fixed dummy password credential");
		return new DefaultIdentityModule(identityStore, passwordHasher, sessionStore, registrationRateLimiter, clock,
				dummyPasswordHash, passwordHashUpgradeFailures, loginRateLimiter);
	}

	@Bean
	PasswordHashUpgradeFailureRecorder passwordHashUpgradeFailureRecorder(MeterRegistry meterRegistry) {
		Counter failures = Counter.builder("cloudforge.iam.password.hash.upgrade.failures")
			.description("Password hash upgrades that could not be persisted")
			.register(meterRegistry);
		return failures::increment;
	}

	@Bean
	RegistrationRateLimiter registrationRateLimiter(RegistrationRateLimitStore store, Clock clock) {
		return new DefaultRegistrationRateLimiter(store, clock);
	}

	@Bean
	LoginRateLimiter loginRateLimiter(LoginRateLimitStore store, Clock clock) {
		return new DefaultLoginRateLimiter(store, clock);
	}

}
