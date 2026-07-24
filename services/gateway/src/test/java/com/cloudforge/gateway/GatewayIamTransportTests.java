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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GatewayIamTransportTests {

	private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

	private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

	private static final String VALID_PROBLEM = "{\"type\":\"urn:cloudforge:problem:iam:rate-limited\","
			+ "\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Try again later\","
			+ "\"instance\":\"/auth/login\",\"code\":\"IAM_RATE_LIMITED\",\"traceId\":\"" + TRACE_ID
			+ "\",\"errors\":[{\"field\":\"email\",\"code\":\"RATE_LIMITED\",\"detail\":\"Wait\"}]}";

	private static final AtomicInteger REGISTER_REQUESTS = new AtomicInteger();

	private static final AtomicInteger LOGIN_REQUESTS = new AtomicInteger();

	private static final ExecutorService IAM_EXECUTOR = Executors.newCachedThreadPool();

	private static final HttpServer IAM_STUB = startIamStub();

	@LocalServerPort
	private int port;

	@DynamicPropertySource
	static void configureIamTransport(DynamicPropertyRegistry registry) {
		registry.add("IAM_BASE_URL", () -> "http://localhost:" + IAM_STUB.getAddress().getPort());
		registry.add("cloudforge.iam.transport.connect-timeout", () -> "500ms");
		registry.add("cloudforge.iam.transport.read-timeout", () -> "500ms");
	}

	@BeforeEach
	void resetRequestCounts() {
		REGISTER_REQUESTS.set(0);
		LOGIN_REQUESTS.set(0);
	}

	@AfterAll
	static void stopIamStub() {
		IAM_STUB.stop(0);
		IAM_EXECUTOR.shutdownNow();
	}

	@Test
	void passesAValidIamProblemResponseThroughUnchanged() throws Exception {
		HttpResponse<String> response = get("/api/v1/iam/test/problem");

		assertThat(response.statusCode()).isEqualTo(429);
		assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json;charset=UTF-8");
		assertThat(response.headers().firstValue("Retry-After")).hasValue("17");
		assertThat(response.body()).isEqualTo(VALID_PROBLEM);
	}

	@Test
	void mapsAnInvalidIamErrorResponseToAStableBadGatewayProblem() throws Exception {
		HttpResponse<String> response = get("/api/v1/iam/test/invalid");

		assertSafeGeneratedProblem(response, 502, "IAM_INVALID_RESPONSE");
	}

	@Test
	void mapsAnIamReadTimeoutToAStableGatewayTimeoutProblem() throws Exception {
		HttpResponse<String> response = get("/api/v1/iam/test/slow");

		assertSafeGeneratedProblem(response, 504, "IAM_DEPENDENCY_TIMEOUT");
	}

	@ParameterizedTest
	@ValueSource(strings = { "register", "login" })
	void sendsEachIdentityWriteRequestOnlyOnceWhenIamFails(String operation) throws Exception {
		HttpResponse<String> response = postWithCsrf("/api/v1/iam/auth/" + operation,
				"{\"email\":\"user@example.com\",\"password\":\"secret-password\"}");

		assertThat(response.statusCode()).isEqualTo(503);
		assertThat(response.body()).contains("\"code\":\"IAM_DEPENDENCY_UNAVAILABLE\"");
		assertThat(operation.equals("register") ? REGISTER_REQUESTS : LOGIN_REQUESTS).hasValue(1);
	}

	private HttpResponse<String> get(String path) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(gatewayUri(path)).header("traceparent", TRACEPARENT).GET().build();
		return send(request);
	}

	private HttpResponse<String> postWithCsrf(String path, String body) throws IOException, InterruptedException {
		HttpResponse<String> csrfResponse = send(HttpRequest.newBuilder(gatewayUri("/api/v1/iam/csrf")).GET().build());
		String csrfCookie = csrfResponse.headers()
			.allValues("Set-Cookie")
			.stream()
			.filter(cookie -> cookie.startsWith("cloudforge_csrf_token="))
			.findFirst()
			.orElseThrow()
			.split(";", 2)[0];
		String csrfToken = csrfCookie.substring(csrfCookie.indexOf('=') + 1);
		HttpRequest request = HttpRequest.newBuilder(gatewayUri(path))
			.header("Content-Type", "application/json")
			.header("Cookie", csrfCookie)
			.header("X-CloudForge-CSRF", csrfToken)
			.header("Origin", gatewayOrigin())
			.header("Sec-Fetch-Site", "same-origin")
			.header("traceparent", TRACEPARENT)
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();
		return send(request);
	}

	private void assertSafeGeneratedProblem(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).isEqualTo(status);
		assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
		assertThat(response.body()).contains("\"status\":" + status, "\"code\":\"" + code + "\"",
				"\"traceId\":\"" + TRACE_ID + "\"");
		assertThat(response.body()).doesNotContain("secret-password", "localhost", "127.0.0.1", "Exception", " at ");
	}

	private URI gatewayUri(String path) {
		return URI.create(gatewayOrigin() + path);
	}

	private String gatewayOrigin() {
		return "http://localhost:" + this.port;
	}

	private static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
		return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
	}

	private static HttpServer startIamStub() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/", GatewayIamTransportTests::handleIamRequest);
			server.setExecutor(IAM_EXECUTOR);
			server.start();
			return server;
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to start IAM test stub", exception);
		}
	}

	private static void handleIamRequest(HttpExchange exchange) throws IOException {
		switch (exchange.getRequestURI().getPath()) {
			case "/test/problem" ->
				respond(exchange, 429, "application/problem+json;charset=UTF-8", VALID_PROBLEM, "17");
			case "/test/invalid" -> respond(exchange, 500, "text/plain", "internal service at 10.0.0.8", null);
			case "/test/slow" -> {
				try {
					Thread.sleep(1_500);
					respond(exchange, 200, "text/plain", "late", null);
				}
				catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					exchange.close();
				}
			}
			case "/auth/register" -> {
				REGISTER_REQUESTS.incrementAndGet();
				respondUnavailable(exchange);
			}
			case "/auth/login" -> {
				LOGIN_REQUESTS.incrementAndGet();
				respondUnavailable(exchange);
			}
			default -> respond(exchange, 404, "text/plain", "not found", null);
		}
	}

	private static void respondUnavailable(HttpExchange exchange) throws IOException {
		String body = "{\"type\":\"urn:cloudforge:problem:iam:dependency-unavailable\","
				+ "\"title\":\"Service Unavailable\",\"status\":503,\"detail\":\"Try again later\","
				+ "\"code\":\"IAM_DEPENDENCY_UNAVAILABLE\",\"traceId\":\"" + TRACE_ID + "\"}";
		respond(exchange, 503, "application/problem+json", body, null);
	}

	private static void respond(HttpExchange exchange, int status, String contentType, String body, String retryAfter)
			throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", contentType);
		if (retryAfter != null) {
			exchange.getResponseHeaders().set("Retry-After", retryAfter);
		}
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream output = exchange.getResponseBody()) {
			output.write(bytes);
		}
	}

}
