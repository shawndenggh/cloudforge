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
package com.cloudforge.gateway;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.function.Consumer;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(classes = { GatewayApplication.class, GatewaySessionExpiryTests.TestClockConfiguration.class },
		webEnvironment = WebEnvironment.RANDOM_PORT)
class GatewaySessionExpiryTests {

	private static final Duration IDLE_TIMEOUT = Duration.ofHours(12);

	private static final Duration ABSOLUTE_TIMEOUT = Duration.ofDays(7);

	private static final HttpServer IAM_STUB = startIamStub();

	@Container
	private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

	private final SessionRepository<?> sessions;

	private final MutableClock clock;

	@LocalServerPort
	private int port;

	@Autowired
	GatewaySessionExpiryTests(SessionRepository<?> sessions, MutableClock clock) {
		this.sessions = sessions;
		this.clock = clock;
	}

	@DynamicPropertySource
	static void infrastructure(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("IAM_BASE_URL", () -> "http://localhost:" + IAM_STUB.getAddress().getPort());
	}

	@AfterAll
	static void stopIamStub() {
		IAM_STUB.stop(0);
	}

	@Test
	void idleBoundaryRejectsAndDeletesSessionWhileEarlierAccessRefreshesIt() throws Exception {
		Session session = createSession(this.sessions,
				value -> value.setLastAccessedTime(Instant.now().minus(IDLE_TIMEOUT).plusSeconds(60)));
		assertThat(session.getMaxInactiveInterval()).isEqualTo(IDLE_TIMEOUT);

		HttpResponse<String> active = getProfile(session);

		assertThat(active.statusCode()).isEqualTo(200);
		Session refreshed = this.sessions.findById(session.getId());
		assertThat(refreshed).isNotNull();
		assertThat(refreshed.getLastAccessedTime()).isAfter(session.getLastAccessedTime());

		updateSession(this.sessions, session.getId(),
				value -> value.setLastAccessedTime(Instant.now().minus(IDLE_TIMEOUT)));
		HttpResponse<String> expired = getProfile(session);

		assertUnauthenticatedAndCookieCleared(expired);
		assertThat(this.sessions.findById(session.getId())).isNull();
	}

	@Test
	void activeAccessNeverExtendsSevenDayAbsoluteDeadline() throws Exception {
		Session session = createSession(this.sessions, value -> {
		});
		this.clock.set(session.getCreationTime().plus(ABSOLUTE_TIMEOUT).minusMillis(1));

		HttpResponse<String> beforeBoundary = getProfile(session);

		assertThat(beforeBoundary.statusCode()).isEqualTo(200);
		Session active = this.sessions.findById(session.getId());
		assertThat(active).isNotNull();
		assertThat(active.getCreationTime()).isEqualTo(session.getCreationTime());

		this.clock.advance(Duration.ofMillis(1));
		HttpResponse<String> atBoundary = getProfile(session);

		assertUnauthenticatedAndCookieCleared(atBoundary);
		assertThat(this.sessions.findById(session.getId())).isNull();
	}

	@Test
	void absoluteExpiryClearsIdentitySessionWithoutBlockingAnonymousCsrfBootstrap() throws Exception {
		Session session = createSession(this.sessions, value -> {
		});
		this.clock.set(session.getCreationTime().plus(ABSOLUTE_TIMEOUT));
		String cookieValue = Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8));

		HttpResponse<String> response = HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/api/v1/iam/csrf"))
				.header("Cookie", "cloudforge_session=" + cookieValue)
				.GET()
				.build(), HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(204);
		assertThat(response.headers().allValues("Set-Cookie"))
			.anySatisfy(cookie -> assertThat(cookie).startsWith("cloudforge_session=").contains("Max-Age=0"));
		assertThat(this.sessions.findById(session.getId())).isNull();
	}

	@Test
	void redisRemovesAShortIdleSessionWithinABoundedPoll() throws Exception {
		Session session = createSession(this.sessions, value -> value.setMaxInactiveInterval(Duration.ofSeconds(2)));

		Instant deadline = Instant.now().plusSeconds(5);
		while (this.sessions.findById(session.getId()) != null && Instant.now().isBefore(deadline)) {
			Thread.sleep(25);
		}

		assertThat(this.sessions.findById(session.getId())).isNull();
		assertUnauthenticatedAndCookieCleared(getProfile(session));
	}

	private HttpResponse<String> getProfile(Session session) throws IOException, InterruptedException {
		String cookieValue = Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8));
		return HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/api/v1/iam/user/profile"))
				.header("Cookie", "cloudforge_session=" + cookieValue)
				.GET()
				.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static void assertUnauthenticatedAndCookieCleared(HttpResponse<String> response) {
		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(response.body()).contains("\"code\":\"SECURITY_UNAUTHENTICATED\"");
		assertThat(response.headers().allValues("Set-Cookie"))
			.anySatisfy(cookie -> assertThat(cookie).startsWith("cloudforge_session=").contains("Max-Age=0"));
	}

	private static <S extends Session> S createSession(SessionRepository<S> sessions, Consumer<S> customizer) {
		S session = sessions.createSession();
		session.setAttribute("user_id", "018f0c24-7a00-7000-8000-000000000001");
		customizer.accept(session);
		sessions.save(session);
		S persisted = sessions.findById(session.getId());
		if (persisted == null) {
			throw new AssertionError("Expected session to be persisted");
		}
		return persisted;
	}

	private static <S extends Session> void updateSession(SessionRepository<S> sessions, String sessionId,
			Consumer<S> customizer) {
		S session = sessions.findById(sessionId);
		if (session == null) {
			throw new AssertionError("Expected session to exist");
		}
		customizer.accept(session);
		sessions.save(session);
	}

	private static HttpServer startIamStub() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/", exchange -> {
				byte[] response = "iam".getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(200, response.length);
				try (OutputStream body = exchange.getResponseBody()) {
					body.write(response);
				}
			});
			server.start();
			return server;
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to start IAM test stub", exception);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClockConfiguration {

		@Bean
		@Primary
		MutableClock gatewayTestClock() {
			return new MutableClock(Instant.now());
		}

	}

	static final class MutableClock extends Clock {

		private volatile Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		void set(Instant instant) {
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
