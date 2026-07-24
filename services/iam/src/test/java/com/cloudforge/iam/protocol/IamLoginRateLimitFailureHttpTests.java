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
import java.time.Duration;
import java.time.Instant;

import com.cloudforge.iam.IamApplication;
import com.cloudforge.iam.identity.LoginRateLimitStore;
import com.cloudforge.iam.identity.LoginRateLimitUnavailableException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
		classes = { IamApplication.class, IamLoginRateLimitFailureHttpTests.FailingRateLimitConfiguration.class },
		webEnvironment = WebEnvironment.RANDOM_PORT)
class IamLoginRateLimitFailureHttpTests {

	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

	@Container
	private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

	@LocalServerPort
	private int port;

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
	void unavailableRateLimitStateFailsClosedBeforeCredentialVerification() throws Exception {
		HttpResponse<String> response = HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/auth/login"))
				.header("Content-Type", "application/json")
				.header("X-CloudForge-Client-IP", "192.0.2.190")
				.POST(HttpRequest.BodyPublishers.ofString("""
						{"email":"unknown@example.com",
						 "password":"wrong password phrase"}
						"""))
				.build(), HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(503);
		assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(response.headers().allValues("Set-Cookie"))
			.noneMatch(cookie -> cookie.startsWith("cloudforge_session="));
		assertThat(response.body()).contains("\"code\":\"PLATFORM_DEPENDENCY_UNAVAILABLE\"", "\"traceId\":\"")
			.doesNotContain("unknown@example.com", "wrong password phrase", "password_hash", "Redis");
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FailingRateLimitConfiguration {

		@Bean
		@Primary
		AlwaysUnavailableLoginRateLimitStore alwaysUnavailableLoginRateLimitStore() {
			return new AlwaysUnavailableLoginRateLimitStore();
		}

	}

	static final class AlwaysUnavailableLoginRateLimitStore implements LoginRateLimitStore {

		@Override
		public Duration check(String normalizedEmail, String clientIp, Instant now) {
			throw new LoginRateLimitUnavailableException();
		}

		@Override
		public Duration recordFailure(String normalizedEmail, String clientIp, Instant now) {
			throw new AssertionError("Failure recording must not run");
		}

		@Override
		public void clearEmail(String normalizedEmail) {
			throw new AssertionError("Success recording must not run");
		}

	}

}
