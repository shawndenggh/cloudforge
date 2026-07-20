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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("production")
@TestPropertySource(properties = "cloudforge.security.allowed-origins=https://attacker.example")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GatewayProductionCorsTests {

	@LocalServerPort
	private int port;

	@Test
	void doesNotEnableCorsInProduction() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/api/v1/iam/csrf"))
			.header("Origin", "https://attacker.example")
			.GET()
			.build();

		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(204);
		assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
		assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
		assertThat(response.headers().allValues("Set-Cookie"))
			.anySatisfy(cookie -> assertThat(cookie).startsWith("cloudforge_csrf_token=").contains("Secure"));
	}

}
