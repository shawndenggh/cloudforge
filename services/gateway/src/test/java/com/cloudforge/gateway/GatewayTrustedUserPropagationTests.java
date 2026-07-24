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
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
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
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GatewayTrustedUserPropagationTests {

	private static final String USER_ID_HEADER = "X-CloudForge-User-Id";

	private static final String USER_ID = "018f0c24-7a00-7000-8000-000000000001";

	private static final String FORGED_USER_ID = "018f0c24-7a00-7000-8000-000000000099";

	private static final AtomicInteger DOWNSTREAM_REQUESTS = new AtomicInteger();

	private static final AtomicReference<String> DOWNSTREAM_USER_ID = new AtomicReference<>();

	private static final HttpServer DOWNSTREAM_STUB = startDownstreamStub();

	@Container
	private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

	private final SessionRepository<?> sessions;

	@LocalServerPort
	private int port;

	@Autowired
	GatewayTrustedUserPropagationTests(SessionRepository<?> sessions) {
		this.sessions = sessions;
	}

	@DynamicPropertySource
	static void infrastructure(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("IAM_BASE_URL", () -> "http://localhost:" + DOWNSTREAM_STUB.getAddress().getPort());
	}

	@BeforeEach
	void resetDownstreamObservations() {
		DOWNSTREAM_REQUESTS.set(0);
		DOWNSTREAM_USER_ID.set(null);
	}

	@AfterAll
	static void stopDownstreamStub() {
		DOWNSTREAM_STUB.stop(0);
	}

	@Test
	void anonymousRequestCannotPropagateAClientSuppliedUserId() throws Exception {
		HttpResponse<String> response = get("/api/v1/iam/auth/register", null);

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(DOWNSTREAM_REQUESTS).hasValue(1);
		assertThat(DOWNSTREAM_USER_ID).hasValue(null);
	}

	@Test
	void validSessionReplacesAClientSuppliedUserIdWithTheCanonicalSessionUser() throws Exception {
		Session session = createSession(this.sessions, USER_ID);

		HttpResponse<String> response = get("/api/v1/iam/user/profile", sessionCookie(session));

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(DOWNSTREAM_REQUESTS).hasValue(1);
		assertThat(DOWNSTREAM_USER_ID).hasValue(USER_ID);
	}

	@Test
	void protectedDownstreamIsNotReachedWithoutAValidSession() throws Exception {
		HttpResponse<String> response = get("/api/v1/iam/user/profile", null);

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.body()).contains("\"code\":\"SECURITY_UNAUTHENTICATED\"");
		assertThat(DOWNSTREAM_REQUESTS).hasValue(0);
	}

	@Test
	void protectedDownstreamRejectsANonCanonicalSessionUser() throws Exception {
		Session session = createSession(this.sessions, USER_ID.toUpperCase(java.util.Locale.ROOT));

		HttpResponse<String> response = get("/api/v1/iam/user/profile", sessionCookie(session));

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.body()).contains("\"code\":\"SECURITY_UNAUTHENTICATED\"");
		assertThat(response.headers().allValues("Set-Cookie"))
			.anySatisfy(cookie -> assertThat(cookie).startsWith("cloudforge_session=").contains("Max-Age=0"));
		assertThat(DOWNSTREAM_REQUESTS).hasValue(0);
		assertThat(this.sessions.findById(session.getId())).isNull();
	}

	private HttpResponse<String> get(String path, String sessionCookie) throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path))
			.header(USER_ID_HEADER, FORGED_USER_ID)
			.GET();
		if (sessionCookie != null) {
			request.header("Cookie", sessionCookie);
		}
		return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static String sessionCookie(Session session) {
		String value = Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8));
		return "cloudforge_session=" + value;
	}

	private static <S extends Session> S createSession(SessionRepository<S> sessions, String userId) {
		S session = sessions.createSession();
		session.setAttribute("user_id", userId);
		sessions.save(session);
		S persisted = sessions.findById(session.getId());
		if (persisted == null) {
			throw new AssertionError("Expected session to be persisted");
		}
		return persisted;
	}

	private static HttpServer startDownstreamStub() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/", exchange -> {
				DOWNSTREAM_REQUESTS.incrementAndGet();
				DOWNSTREAM_USER_ID.set(exchange.getRequestHeaders().getFirst(USER_ID_HEADER));
				byte[] response = "downstream".getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(200, response.length);
				try (OutputStream body = exchange.getResponseBody()) {
					body.write(response);
				}
			});
			server.start();
			return server;
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to start downstream test stub", exception);
		}
	}

}
