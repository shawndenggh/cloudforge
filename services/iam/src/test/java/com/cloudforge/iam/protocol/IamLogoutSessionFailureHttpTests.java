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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import com.cloudforge.iam.IamApplication;
import com.cloudforge.iam.identity.IdentitySessionRevocationUnavailableException;
import com.cloudforge.iam.identity.IdentitySessionStore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = { IamApplication.class, IamLogoutSessionFailureHttpTests.FailureConfiguration.class },
		webEnvironment = WebEnvironment.RANDOM_PORT)
class IamLogoutSessionFailureHttpTests {

	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

	@Container
	private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

	private final SessionRepository<?> sessions;

	private final ControllableSessionStore sessionStore;

	@LocalServerPort
	private int port;

	@Autowired
	IamLogoutSessionFailureHttpTests(SessionRepository<?> sessions, ControllableSessionStore sessionStore) {
		this.sessions = sessions;
		this.sessionStore = sessionStore;
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
	void uncertainDeletionKeepsCookiesAndTheOriginalCookieCanRetryAfterRecovery() throws Exception {
		Session session = createSession(this.sessions);
		String cookie = "cloudforge_session="
				+ Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8));

		HttpResponse<String> unavailable = logout(cookie);

		assertThat(unavailable.statusCode()).isEqualTo(503);
		assertThat(unavailable.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(unavailable.body()).contains("\"code\":\"IAM_SESSION_REVOCATION_UNAVAILABLE\"", "\"traceId\":\"")
			.doesNotContain(session.getId(), cookie, "Redis", "java.");
		assertThat(unavailable.headers().allValues("Set-Cookie"))
			.noneMatch(value -> value.startsWith("cloudforge_session=") || value.startsWith("cloudforge_csrf_token="));
		assertThat(this.sessions.findById(session.getId())).isNotNull();

		this.sessionStore.recover();
		HttpResponse<String> recovered = logout(cookie);

		assertThat(recovered.statusCode()).isEqualTo(204);
		assertThat(recovered.headers().allValues("Set-Cookie"))
			.anyMatch(value -> value.startsWith("cloudforge_session=") && value.contains("Max-Age=0"))
			.anyMatch(value -> value.startsWith("cloudforge_csrf_token=") && value.contains("Max-Age=0"));
		assertThat(this.sessions.findById(session.getId())).isNull();
	}

	private HttpResponse<String> logout(String cookie) throws Exception {
		return HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/auth/logout"))
				.header("Cookie", cookie)
				.POST(HttpRequest.BodyPublishers.noBody())
				.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static <S extends Session> S createSession(SessionRepository<S> sessions) {
		S session = sessions.createSession();
		session.setAttribute("user_id", UUID.fromString("018f0c24-7a00-7000-8000-000000000001").toString());
		sessions.save(session);
		S persisted = sessions.findById(session.getId());
		if (persisted == null) {
			throw new AssertionError("Expected session to be persisted");
		}
		return persisted;
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FailureConfiguration {

		@Bean
		@Primary
		ControllableSessionStore controllableSessionStore(
				@Qualifier("redisIdentitySessionStore") IdentitySessionStore delegate) {
			return new ControllableSessionStore(delegate);
		}

	}

	static final class ControllableSessionStore implements IdentitySessionStore {

		private final IdentitySessionStore delegate;

		private volatile boolean unavailable = true;

		ControllableSessionStore(IdentitySessionStore delegate) {
			this.delegate = delegate;
		}

		@Override
		public String create(UUID userId) {
			return this.delegate.create(userId);
		}

		@Override
		public void revoke(String sessionId) {
			if (this.unavailable) {
				throw new IdentitySessionRevocationUnavailableException();
			}
			this.delegate.revoke(sessionId);
		}

		void recover() {
			this.unavailable = false;
		}

	}

}
