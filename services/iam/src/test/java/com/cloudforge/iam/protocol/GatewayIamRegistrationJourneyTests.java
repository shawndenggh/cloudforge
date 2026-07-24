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

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.cloudforge.gateway.GatewayApplication;
import com.cloudforge.iam.IamApplication;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = GatewayApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = { "cloudforge.security.secure-cookies=false",
				"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
						+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
						+ "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration" })
class GatewayIamRegistrationJourneyTests {

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

	private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

	private static final ConfigurableApplicationContext IAM = startIam();

	@LocalServerPort
	private int port;

	@DynamicPropertySource
	static void iamRoute(DynamicPropertyRegistry registry) {
		WebServerApplicationContext server = (WebServerApplicationContext) IAM;
		registry.add("spring.cloud.gateway.server.webmvc.routes[0].id", () -> "iam-api");
		registry.add("spring.cloud.gateway.server.webmvc.routes[0].uri",
				() -> "http://localhost:" + server.getWebServer().getPort());
		registry.add("spring.cloud.gateway.server.webmvc.routes[0].predicates[0]", () -> "Path=/api/v1/iam/**");
		registry.add("spring.cloud.gateway.server.webmvc.routes[0].filters[0]", () -> "StripPrefix=3");
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
	}

	@AfterAll
	static void stopInfrastructure() {
		IAM.close();
		REDIS.stop();
		POSTGRES.stop();
	}

	@Test
	void completesTheMinimumMultiDeviceIdentityJourneyThroughGateway() throws Exception {
		CookieManager firstCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		HttpClient firstClient = HttpClient.newBuilder()
			.cookieHandler(firstCookies)
			.connectTimeout(Duration.ofSeconds(5))
			.build();

		String registrationCsrf = csrfToken(firstClient);
		HttpResponse<String> registration = firstClient.send(HttpRequest.newBuilder(uri("/api/v1/iam/auth/register"))
			.header("Content-Type", "application/json")
			.header("X-CloudForge-CSRF", registrationCsrf)
			.header("Origin", origin())
			.header("Sec-Fetch-Site", "same-origin")
			.POST(HttpRequest.BodyPublishers.ofString("""
					{
					  "email": "journey@example.com",
					  "password": "correct horse battery staple",
					  "confirmPassword": "correct horse battery staple"
					}
					"""))
			.build(), HttpResponse.BodyHandlers.ofString());

		assertThat(registration.statusCode()).isEqualTo(201);
		assertThat(registration.headers().allValues("Set-Cookie"))
			.anyMatch(value -> value.startsWith("cloudforge_session="));

		HttpResponse<String> firstProfile = firstClient.send(
				HttpRequest.newBuilder(uri("/api/v1/iam/user/profile")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(firstProfile.statusCode()).isEqualTo(200);
		assertThat(firstProfile.headers().firstValue("Cache-Control")).contains("no-store");
		assertThat(firstProfile.body()).contains("\"email\":\"journey@example.com\"");

		CookieManager secondCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		HttpClient secondClient = HttpClient.newBuilder().cookieHandler(secondCookies).build();
		String loginCsrf = csrfToken(secondClient);
		HttpResponse<String> login = secondClient.send(HttpRequest.newBuilder(uri("/api/v1/iam/auth/login"))
			.header("Content-Type", "application/json")
			.header("X-CloudForge-CSRF", loginCsrf)
			.header("Origin", origin())
			.header("Sec-Fetch-Site", "same-origin")
			.POST(HttpRequest.BodyPublishers.ofString("""
					{"email":"journey@example.com",
					 "password":"correct horse battery staple"}
					"""))
			.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(login.statusCode()).isEqualTo(201);

		String logoutCsrf = csrfToken(firstClient);
		HttpResponse<String> logout = firstClient.send(HttpRequest.newBuilder(uri("/api/v1/iam/auth/logout"))
			.header("X-CloudForge-CSRF", logoutCsrf)
			.header("Origin", origin())
			.header("Sec-Fetch-Site", "same-origin")
			.POST(HttpRequest.BodyPublishers.noBody())
			.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(logout.statusCode()).isEqualTo(204);
		assertThat(logout.headers().allValues("Set-Cookie"))
			.anyMatch(value -> value.startsWith("cloudforge_session=") && value.contains("Max-Age=0"))
			.anyMatch(value -> value.startsWith("cloudforge_csrf_token=") && value.contains("Max-Age=0"));

		HttpResponse<String> loggedOutProfile = firstClient.send(
				HttpRequest.newBuilder(uri("/api/v1/iam/user/profile")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> secondProfile = secondClient.send(
				HttpRequest.newBuilder(uri("/api/v1/iam/user/profile")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(loggedOutProfile.statusCode()).isEqualTo(401);
		assertThat(loggedOutProfile.body()).contains("\"code\":\"SECURITY_UNAUTHENTICATED\"");
		assertThat(secondProfile.statusCode()).isEqualTo(200);
		assertThat(secondProfile.body()).contains("\"email\":\"journey@example.com\"");
	}

	private String csrfToken(HttpClient client) throws Exception {
		HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/api/v1/iam/csrf")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(204);
		String cookie = response.headers()
			.allValues("Set-Cookie")
			.stream()
			.filter(value -> value.startsWith("cloudforge_csrf_token="))
			.findFirst()
			.orElseThrow()
			.split(";", 2)[0];
		return cookie.substring(cookie.indexOf('=') + 1);
	}

	private URI uri(String path) {
		return URI.create(origin() + path);
	}

	private String origin() {
		return "http://localhost:" + this.port;
	}

	private static ConfigurableApplicationContext startIam() {
		Startables.deepStart(POSTGRES, REDIS).join();
		return new SpringApplicationBuilder(IamApplication.class).run("--server.port=0",
				"--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
				"--spring.datasource.username=" + POSTGRES.getUsername(),
				"--spring.datasource.password=" + POSTGRES.getPassword(), "--spring.data.redis.host=" + REDIS.getHost(),
				"--spring.data.redis.port=" + REDIS.getMappedPort(6379),
				"--spring.rabbitmq.listener.simple.auto-startup=false",
				"--spring.rabbitmq.listener.direct.auto-startup=false", "--cloudforge.security.secure-cookies=false");
	}

}
