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

import java.io.OutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
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
class GatewayCsrfTests {

	private static final AtomicInteger IAM_REQUESTS = new AtomicInteger();

	private static final AtomicReference<String> IAM_REQUEST_PATH = new AtomicReference<>();

	private static final HttpServer IAM_STUB = startIamStub();

	@LocalServerPort
	private int port;

	@DynamicPropertySource
	static void configureIamStub(DynamicPropertyRegistry registry) {
		registry.add("IAM_BASE_URL", () -> "http://localhost:" + IAM_STUB.getAddress().getPort());
	}

	@AfterAll
	static void stopIamStub() {
		IAM_STUB.stop(0);
	}

	@Test
	void providesCsrfTokenWithoutCreatingSession() throws IOException, InterruptedException {
		HttpResponse<String> response = send(HttpRequest.newBuilder(csrfUri()).GET().build());

		assertThat(response.statusCode()).isEqualTo(204);
		assertThat(response.body()).isEmpty();
		assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
		assertThat(response.headers().allValues("Set-Cookie"))
			.anySatisfy(cookie -> assertThat(cookie).startsWith("cloudforge_csrf_token=")
				.contains("Path=/", "Secure", "SameSite=Lax")
				.doesNotContain("Domain=", "HttpOnly", "Max-Age="))
			.noneMatch(cookie -> cookie.startsWith("cloudforge_session=") || cookie.startsWith("JSESSIONID="));
	}

	@Test
	void reusesExistingCsrfToken() throws IOException, InterruptedException {
		HttpResponse<String> firstResponse = send(HttpRequest.newBuilder(csrfUri()).GET().build());
		String csrfCookie = firstResponse.headers()
			.allValues("Set-Cookie")
			.stream()
			.filter(cookie -> cookie.startsWith("cloudforge_csrf_token="))
			.findFirst()
			.orElseThrow()
			.split(";", 2)[0];

		HttpResponse<String> secondResponse = send(
				HttpRequest.newBuilder(csrfUri()).header("Cookie", csrfCookie).GET().build());

		assertThat(secondResponse.statusCode()).isEqualTo(204);
		assertThat(secondResponse.headers().allValues("Set-Cookie"))
			.noneMatch(cookie -> cookie.startsWith("cloudforge_csrf_token="));
	}

	@ParameterizedTest
	@ValueSource(strings = { "POST", "PUT", "PATCH", "DELETE" })
	void rejectsWriteWithoutCsrfTokenBeforeRouting(String method) throws IOException, InterruptedException {
		IAM_REQUESTS.set(0);
		HttpRequest request = HttpRequest.newBuilder(gatewayUri("/api/v1/iam/auth/register"))
			.header("Content-Type", "application/json")
			.header("Origin", gatewayOrigin())
			.header("Sec-Fetch-Site", "same-origin")
			.method(method, HttpRequest.BodyPublishers.ofString("{}"))
			.build();

		HttpResponse<String> response = send(request);

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
		assertThat(response.body()).contains("\"code\":\"SECURITY_CSRF_INVALID\"");
		assertThat(IAM_REQUESTS).hasValue(0);
	}

	@Test
	void rejectsWriteFromCrossSiteOriginBeforeRouting() throws IOException, InterruptedException {
		IAM_REQUESTS.set(0);
		HttpResponse<String> csrfResponse = send(HttpRequest.newBuilder(csrfUri()).GET().build());
		String csrfCookie = csrfResponse.headers()
			.allValues("Set-Cookie")
			.stream()
			.filter(cookie -> cookie.startsWith("cloudforge_csrf_token="))
			.findFirst()
			.orElseThrow()
			.split(";", 2)[0];
		String csrfToken = csrfCookie.substring(csrfCookie.indexOf('=') + 1);
		HttpRequest request = HttpRequest.newBuilder(gatewayUri("/api/v1/iam/auth/login"))
			.header("Content-Type", "application/json")
			.header("Cookie", csrfCookie)
			.header("X-CloudForge-CSRF", csrfToken)
			.header("Origin", "https://attacker.example")
			.header("Sec-Fetch-Site", "same-origin")
			.POST(HttpRequest.BodyPublishers.ofString("{}"))
			.build();

		HttpResponse<String> response = send(request);

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.body()).contains("\"code\":\"SECURITY_CSRF_INVALID\"");
		assertThat(IAM_REQUESTS).hasValue(0);
	}

	@Test
	void rejectsWriteWithCrossSiteFetchMetadataBeforeRouting() throws IOException, InterruptedException {
		IAM_REQUESTS.set(0);
		HttpResponse<String> csrfResponse = send(HttpRequest.newBuilder(csrfUri()).GET().build());
		String csrfCookie = csrfResponse.headers()
			.allValues("Set-Cookie")
			.stream()
			.filter(cookie -> cookie.startsWith("cloudforge_csrf_token="))
			.findFirst()
			.orElseThrow()
			.split(";", 2)[0];
		String csrfToken = csrfCookie.substring(csrfCookie.indexOf('=') + 1);
		HttpRequest request = HttpRequest.newBuilder(gatewayUri("/api/v1/iam/auth/logout"))
			.header("Cookie", csrfCookie)
			.header("X-CloudForge-CSRF", csrfToken)
			.header("Origin", gatewayOrigin())
			.header("Sec-Fetch-Site", "cross-site")
			.POST(HttpRequest.BodyPublishers.noBody())
			.build();

		HttpResponse<String> response = send(request);

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.body()).contains("\"code\":\"SECURITY_CSRF_INVALID\"");
		assertThat(IAM_REQUESTS).hasValue(0);
	}

	@Test
	void routesWriteWithTrustedRefererAndCsrfToken() throws IOException, InterruptedException {
		IAM_REQUESTS.set(0);
		IAM_REQUEST_PATH.set(null);
		HttpResponse<String> csrfResponse = send(HttpRequest.newBuilder(csrfUri()).GET().build());
		String csrfCookie = csrfResponse.headers()
			.allValues("Set-Cookie")
			.stream()
			.filter(cookie -> cookie.startsWith("cloudforge_csrf_token="))
			.findFirst()
			.orElseThrow()
			.split(";", 2)[0];
		String csrfToken = csrfCookie.substring(csrfCookie.indexOf('=') + 1);
		HttpRequest request = HttpRequest.newBuilder(gatewayUri("/api/v1/iam/auth/register"))
			.header("Content-Type", "application/json")
			.header("Cookie", csrfCookie)
			.header("X-CloudForge-CSRF", csrfToken)
			.header("Referer", gatewayOrigin() + "/register")
			.header("Sec-Fetch-Site", "same-origin")
			.POST(HttpRequest.BodyPublishers.ofString("{}"))
			.build();

		HttpResponse<String> response = send(request);

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("iam");
		assertThat(IAM_REQUESTS).hasValue(1);
		assertThat(IAM_REQUEST_PATH).hasValue("/auth/register");
	}

	private URI csrfUri() {
		return gatewayUri("/api/v1/iam/csrf");
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
			server.createContext("/", exchange -> {
				IAM_REQUESTS.incrementAndGet();
				IAM_REQUEST_PATH.set(exchange.getRequestURI().getPath());
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

}
