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
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GatewayIamConnectionFailureTests {

	private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

	private static final int CLOSED_PORT = findClosedPort();

	@LocalServerPort
	private int port;

	@DynamicPropertySource
	static void configureUnavailableIam(DynamicPropertyRegistry registry) {
		registry.add("IAM_BASE_URL", () -> "http://localhost:" + CLOSED_PORT);
		registry.add("cloudforge.iam.transport.connect-timeout", () -> "500ms");
		registry.add("cloudforge.iam.transport.read-timeout", () -> "500ms");
	}

	@Test
	void mapsAnIamConnectionFailureToAStableBadGatewayProblem() throws Exception {
		String traceparent = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
		HttpRequest request = HttpRequest
			.newBuilder(URI.create("http://localhost:" + this.port + "/api/v1/iam/test/unavailable"))
			.header("traceparent", traceparent)
			.GET()
			.build();

		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(502);
		assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
		assertThat(response.body()).contains("\"status\":502", "\"code\":\"IAM_BAD_GATEWAY\"",
				"\"traceId\":\"" + TRACE_ID + "\"");
		assertThat(response.body()).doesNotContain("localhost", Integer.toString(CLOSED_PORT), "Exception", " at ");
	}

	private static int findClosedPort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to reserve a closed test port", exception);
		}
	}

}
