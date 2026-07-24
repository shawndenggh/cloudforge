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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cloudforge.iam.IamApplication;
import org.flywaydb.core.Flyway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = IamApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class IamRegistrationProfileHttpTests {

	private static final Pattern PROFILE_JSON = Pattern
		.compile("\\{\"id\":\"([^\"]+)\",\"email\":\"new\\.user@example\\.com\",\"registeredAt\":\"[^\"]+\"}");

	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

	@Container
	private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Flyway flyway;

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
	void registrationCreatesSessionThatReadsExactUserProfile() throws Exception {
		CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		HttpClient client = HttpClient.newBuilder()
			.cookieHandler(cookies)
			.connectTimeout(Duration.ofSeconds(5))
			.build();

		HttpResponse<String> registration = client.send(HttpRequest.newBuilder(uri("/auth/register"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString("""
					{
					  "email": "  New.User@Example.COM ",
					  "password": "correct horse battery staple",
					  "confirmPassword": "correct horse battery staple"
					}
					"""))
			.build(), HttpResponse.BodyHandlers.ofString());

		assertThat(registration.statusCode()).isEqualTo(201);
		assertThat(registration.body()).isEmpty();
		assertThat(registration.headers().firstValue("Location")).isEmpty();
		assertThat(registration.headers().allValues("Set-Cookie"))
			.anySatisfy(value -> assertThat(value).startsWith("cloudforge_session=")
				.contains("Path=/", "Max-Age=604800", "HttpOnly", "SameSite=Lax")
				.doesNotContain("Domain=", "Secure"));

		HttpResponse<String> profile = client.send(HttpRequest.newBuilder(uri("/user/profile")).GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(profile.statusCode()).isEqualTo(200);
		assertThat(profile.headers().firstValue("Cache-Control")).contains("no-store");
		Matcher profileJson = PROFILE_JSON.matcher(profile.body());
		assertThat(profileJson.matches()).isTrue();
		assertThat(UUID.fromString(profileJson.group(1)).version()).isEqualTo(7);

		String passwordHash = this.jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class,
				"new.user@example.com");
		assertThat(passwordHash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$")
			.doesNotContain("correct horse battery staple");
		int hashSeparator = passwordHash.lastIndexOf('$');
		int saltSeparator = passwordHash.lastIndexOf('$', hashSeparator - 1);
		assertThat(Base64.getDecoder().decode(passwordHash.substring(saltSeparator + 1, hashSeparator))).hasSize(16);
		assertThat(Base64.getDecoder().decode(passwordHash.substring(hashSeparator + 1))).hasSize(32);

		this.jdbc.update("DELETE FROM users WHERE email = ?", "new.user@example.com");
		HttpResponse<String> missingUser = client.send(HttpRequest.newBuilder(uri("/user/profile")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertUnauthenticated(missingUser);
		assertThat(missingUser.headers().allValues("Set-Cookie"))
			.anySatisfy(value -> assertThat(value).startsWith("cloudforge_session=").contains("Max-Age=0"));
	}

	@Test
	void profileWithoutSessionIsUnauthenticated() throws Exception {
		HttpClient client = HttpClient.newHttpClient();

		HttpResponse<String> profile = client.send(HttpRequest.newBuilder(uri("/user/profile")).GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertUnauthenticated(profile);
	}

	@Test
	void profileWithUnknownSessionIsUnauthenticated() throws Exception {
		String unknownSession = Base64.getEncoder().encodeToString("unknown-session".getBytes(StandardCharsets.UTF_8));
		HttpResponse<String> profile = HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(uri("/user/profile"))
				.header("Cookie", "cloudforge_session=" + unknownSession)
				.GET()
				.build(), HttpResponse.BodyHandlers.ofString());

		assertUnauthenticated(profile);
	}

	@Test
	void migrationCreatesOnlyApprovedUserColumnsAndCanRunAgain() {
		List<String> columns = this.jdbc.queryForList("""
				SELECT column_name
				FROM information_schema.columns
				WHERE table_schema = 'public' AND table_name = 'users'
				ORDER BY ordinal_position
				""", String.class);

		assertThat(columns).containsExactly("id", "email", "password_hash", "created_at");
		assertThat(this.jdbc.queryForObject("""
				SELECT count(*)
				FROM information_schema.table_constraints
				WHERE table_schema = 'public'
				  AND table_name = 'users'
				  AND constraint_name = 'users_email_key'
				  AND constraint_type = 'UNIQUE'
				""", Integer.class)).isEqualTo(1);
		assertThat(this.jdbc.queryForList("""
				SELECT constraint_name
				FROM information_schema.table_constraints
				WHERE table_schema = 'public'
				  AND table_name = 'users'
				  AND constraint_type = 'CHECK'
				  AND constraint_name IN ('users_email_lowercase_check', 'users_email_trimmed_check')
				ORDER BY constraint_name
				""", String.class)).containsExactly("users_email_lowercase_check", "users_email_trimmed_check");
		assertThat(this.flyway.migrate().migrationsExecuted).isZero();
	}

	@Test
	void iamRestartsAgainstTheMigratedDatabaseWithJpaValidation() {
		try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(IamApplication.class).run(
				"--server.port=0", "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
				"--spring.datasource.username=" + POSTGRES.getUsername(),
				"--spring.datasource.password=" + POSTGRES.getPassword(), "--spring.data.redis.host=" + REDIS.getHost(),
				"--spring.data.redis.port=" + REDIS.getMappedPort(6379),
				"--spring.rabbitmq.listener.simple.auto-startup=false",
				"--spring.rabbitmq.listener.direct.auto-startup=false", "--cloudforge.security.secure-cookies=false")) {
			assertThat(ignored.isActive()).isTrue();
		}
	}

	@Test
	void authorizationServerMetadataIsNotExposed() throws Exception {
		HttpResponse<String> metadata = HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(uri("/.well-known/oauth-authorization-server")).GET().build(),
					HttpResponse.BodyHandlers.ofString());

		assertThat(metadata.statusCode()).isNotEqualTo(200);
		assertThat(metadata.body()).doesNotContain("\"issuer\"");
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + this.port + path);
	}

	private static void assertUnauthenticated(HttpResponse<String> response) {
		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(response.body()).contains("\"code\":\"SECURITY_UNAUTHENTICATED\"");
	}

}
