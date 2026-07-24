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
package com.cloudforge.iam.protocol;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.cloudforge.iam.IamApplication;
import com.cloudforge.iam.identity.IdentitySessionStore;
import com.cloudforge.iam.identity.IdentitySessionUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
		classes = { IamApplication.class, IamRegistrationSessionFailureHttpTests.FailingSessionConfiguration.class },
		webEnvironment = WebEnvironment.RANDOM_PORT)
class IamRegistrationSessionFailureHttpTests {

	private static final String EMAIL = "session-recovery@example.com";

	private static final String PASSWORD = "correct horse battery staple";

	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

	@Container
	private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

	private final JdbcTemplate jdbc;

	private final PasswordEncoder passwordEncoder;

	private final AlwaysFailingSessionStore sessions;

	private final MeterRegistry meterRegistry;

	@LocalServerPort
	private int port;

	@Autowired
	IamRegistrationSessionFailureHttpTests(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
			AlwaysFailingSessionStore sessions, MeterRegistry meterRegistry) {
		this.jdbc = jdbc;
		this.passwordEncoder = passwordEncoder;
		this.sessions = sessions;
		this.meterRegistry = meterRegistry;
	}

	@DynamicPropertySource
	static void infrastructure(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> false);
		registry.add("spring.rabbitmq.listener.direct.auto-startup", () -> false);
		registry.add("cloudforge.security.secure-cookies", () -> false);
	}

	@Test
	void reportsRecoverableFailureWithoutDeletingUserOrCreatingDuplicate() throws Exception {
		HttpResponse<String> failedRegistration = register("192.0.2.140");

		assertThat(failedRegistration.statusCode()).isEqualTo(503);
		assertThat(failedRegistration.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(failedRegistration.headers().allValues("Set-Cookie"))
			.noneMatch(cookie -> cookie.startsWith("cloudforge_session="));
		assertThat(failedRegistration.body())
			.contains("\"code\":\"IAM_REGISTRATION_COMPLETED_SESSION_UNAVAILABLE\"", "\"traceId\":\"")
			.doesNotContain(EMAIL, PASSWORD, "password_hash", "Redis");
		assertThat(this.sessions.attempts()).isEqualTo(3);
		assertThat(this.meterRegistry.find("cloudforge.iam.registration.session.creation.failures").counter())
			.satisfies(counter -> assertThat(counter.count()).isEqualTo(1));
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, EMAIL))
			.isEqualTo(1);
		String passwordHash = this.jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class,
				EMAIL);
		assertThat(this.passwordEncoder.matches(PASSWORD, passwordHash)).isTrue();

		HttpResponse<String> duplicateRegistration = register("192.0.2.141");

		assertThat(duplicateRegistration.statusCode()).isEqualTo(409);
		assertThat(duplicateRegistration.body()).contains("\"code\":\"IAM_EMAIL_ALREADY_REGISTERED\"");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, EMAIL))
			.isEqualTo(1);
		assertThat(this.sessions.attempts()).isEqualTo(3);
	}

	private HttpResponse<String> register(String clientIp) throws Exception {
		return HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/auth/register"))
				.header("Content-Type", "application/json")
				.header("X-CloudForge-Client-IP", clientIp)
				.POST(HttpRequest.BodyPublishers.ofString("""
						{"email":"%s",
						 "password":"%s",
						 "confirmPassword":"%s"}
						""".formatted(EMAIL, PASSWORD, PASSWORD)))
				.build(), HttpResponse.BodyHandlers.ofString());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FailingSessionConfiguration {

		@Bean
		@Primary
		AlwaysFailingSessionStore alwaysFailingSessionStore() {
			return new AlwaysFailingSessionStore();
		}

	}

	static final class AlwaysFailingSessionStore implements IdentitySessionStore {

		private final AtomicInteger attempts = new AtomicInteger();

		@Override
		public String create(UUID userId) {
			this.attempts.incrementAndGet();
			throw new IdentitySessionUnavailableException();
		}

		int attempts() {
			return this.attempts.get();
		}

	}

}
