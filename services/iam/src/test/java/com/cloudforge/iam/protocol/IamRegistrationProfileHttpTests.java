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
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.cloudforge.iam.IamApplication;
import com.password4j.Argon2Function;
import com.password4j.Password;
import com.password4j.types.Argon2;
import org.flywaydb.core.Flyway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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

	private static final AtomicInteger CLIENT_IP_SEQUENCE = new AtomicInteger();

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

	@Autowired
	private StringRedisTemplate redis;

	@Autowired
	private PasswordEncoder passwordEncoder;

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
			.header("X-CloudForge-Client-IP", nextClientIp())
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
	void twoClientsCanLogInToTheSameUserWithIndependentSessions() throws Exception {
		String password = "correct horse battery staple";
		HttpResponse<String> registration = postRegistration(HttpClient.newHttpClient(), """
				{"email":"multi-device@example.com",
				 "password":"%s",
				 "confirmPassword":"%s"}
				""".formatted(password, password), "application/json");
		assertThat(registration.statusCode()).isEqualTo(201);

		CookieManager firstCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		CookieManager secondCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		HttpClient firstClient = HttpClient.newBuilder().cookieHandler(firstCookies).build();
		HttpClient secondClient = HttpClient.newBuilder().cookieHandler(secondCookies).build();

		HttpResponse<String> firstLogin = postLogin(firstClient, """
				{"email":"  Multi-Device@Example.COM ",
				 "password":"%s",
				 "futureField":true}
				""".formatted(password));
		HttpResponse<String> secondLogin = postLogin(secondClient, """
				{"email":"multi-device@example.com",
				 "password":"%s"}
				""".formatted(password));

		assertSuccessfulLogin(firstLogin);
		assertSuccessfulLogin(secondLogin);
		assertThat(sessionCookie(firstCookies)).isNotEqualTo(sessionCookie(secondCookies));

		HttpResponse<String> firstProfile = firstClient.send(HttpRequest.newBuilder(uri("/user/profile")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> secondProfile = secondClient
			.send(HttpRequest.newBuilder(uri("/user/profile")).GET().build(), HttpResponse.BodyHandlers.ofString());
		assertThat(firstProfile.statusCode()).isEqualTo(200);
		assertThat(secondProfile.statusCode()).isEqualTo(200);
		assertThat(firstProfile.body()).contains("\"email\":\"multi-device@example.com\"");
		assertThat(secondProfile.body()).isEqualTo(firstProfile.body());
	}

	@Test
	void successfulLoginReplacesAnUnknownClientChosenSessionId() throws Exception {
		String password = "correct horse battery staple";
		HttpResponse<String> registration = postRegistration(HttpClient.newHttpClient(), """
				{"email":"session-fixation@example.com",
				 "password":"%s",
				 "confirmPassword":"%s"}
				""".formatted(password, password), "application/json");
		assertThat(registration.statusCode()).isEqualTo(201);
		String clientChosenSession = Base64.getEncoder()
			.encodeToString("attacker-chosen-session".getBytes(StandardCharsets.UTF_8));

		HttpResponse<String> login = HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(uri("/auth/login"))
				.header("Content-Type", "application/json")
				.header("Cookie", "cloudforge_session=" + clientChosenSession)
				.POST(HttpRequest.BodyPublishers.ofString("""
						{"email":"session-fixation@example.com",
						 "password":"%s"}
						""".formatted(password)))
				.build(), HttpResponse.BodyHandlers.ofString());

		assertSuccessfulLogin(login);
		assertThat(login.headers().allValues("Set-Cookie")).filteredOn(value -> value.startsWith("cloudforge_session="))
			.singleElement()
			.satisfies(value -> assertThat(value).doesNotStartWith("cloudforge_session=" + clientChosenSession + ";"));
	}

	@Test
	void loginUsesOneCredentialFailureContractWithoutLeakingAccountExistence() throws Exception {
		String password = "correct horse battery staple";
		HttpResponse<String> registration = postRegistration(HttpClient.newHttpClient(), """
				{"email":"credential-check@example.com",
				 "password":"%s",
				 "confirmPassword":"%s"}
				""".formatted(password, password), "application/json");
		assertThat(registration.statusCode()).isEqualTo(201);

		HttpResponse<String> wrongPassword = postLogin(HttpClient.newHttpClient(), """
				{"email":"credential-check@example.com",
				 "password":"wrong password phrase"}
				""");
		HttpResponse<String> unknownEmail = postLogin(HttpClient.newHttpClient(), """
				{"email":"missing@example.com",
				 "password":"wrong password phrase"}
				""");

		assertInvalidCredentials(wrongPassword);
		assertInvalidCredentials(unknownEmail);
		assertThat(withoutTraceId(wrongPassword.body())).isEqualTo(withoutTraceId(unknownEmail.body()));
	}

	@Test
	void successfulLoginUpgradesAnOlderArgon2idHashWithANewSalt() throws Exception {
		String email = "old-password-hash@example.com";
		String password = "correct horse battery staple";
		HttpResponse<String> registration = postRegistration(HttpClient.newHttpClient(), """
				{"email":"%s",
				 "password":"%s",
				 "confirmPassword":"%s"}
				""".formatted(email, password, password), "application/json");
		assertThat(registration.statusCode()).isEqualTo(201);
		Argon2Function oldFunction = Argon2Function.getInstance(12_288, 1, 1, 32, Argon2.ID);
		String oldPasswordHash = Objects
			.requireNonNull(Password.hash(password).addRandomSalt(16).with(oldFunction).getResult());
		this.jdbc.update("UPDATE users SET password_hash = ? WHERE email = ?", oldPasswordHash, email);
		assertThat(this.passwordEncoder.matches(password, oldPasswordHash)).isTrue();
		assertThat(this.passwordEncoder.upgradeEncoding(oldPasswordHash)).isTrue();

		HttpResponse<String> login = postLogin(HttpClient.newHttpClient(), """
				{"email":"%s",
				 "password":"%s"}
				""".formatted(email, password));

		assertSuccessfulLogin(login);
		String upgradedPasswordHash = this.jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?",
				String.class, email);
		assertThat(upgradedPasswordHash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$").isNotEqualTo(oldPasswordHash);
		assertThat(argon2Salt(upgradedPasswordHash)).isNotEqualTo(argon2Salt(oldPasswordHash));
		assertThat(this.passwordEncoder.matches(password, upgradedPasswordHash)).isTrue();
		assertThat(this.passwordEncoder.upgradeEncoding(upgradedPasswordHash)).isFalse();
	}

	@Test
	void loginWithValidSessionReturnsConflictBeforeCredentialValidation() throws Exception {
		CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
		String password = "correct horse battery staple";
		HttpResponse<String> registration = postRegistration(client, """
				{"email":"already-authenticated-login@example.com",
				 "password":"%s",
				 "confirmPassword":"%s"}
				""".formatted(password, password), "application/json");
		assertThat(registration.statusCode()).isEqualTo(201);
		String originalSession = sessionCookie(cookies);

		HttpResponse<String> conflict = postLogin(client, "{");

		assertThat(conflict.statusCode()).isEqualTo(409);
		assertThat(conflict.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(conflict.body()).contains("\"code\":\"IAM_ALREADY_AUTHENTICATED\"", "\"traceId\":\"")
			.doesNotContain("IAM_REQUEST_BODY_INVALID", "password", "email");
		assertThat(sessionCookie(cookies)).isEqualTo(originalSession);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidLoginRequests")
	void loginReturnsFieldProblemDetails(String description, String body, String field, String fieldCode)
			throws Exception {
		HttpResponse<String> response = postLogin(HttpClient.newHttpClient(), body);

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(response.body())
			.contains("\"type\":\"urn:cloudforge:problem:iam:validation-failed\"",
					"\"title\":\"Login validation failed\"", "\"status\":400", "\"instance\":\"/auth/login\"",
					"\"code\":\"IAM_VALIDATION_FAILED\"", "\"traceId\":\"", "\"field\":\"" + field + "\"",
					"\"code\":\"" + fieldCode + "\"")
			.doesNotContain("password_hash", "argon2", "java.");
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidRegistrationRequests")
	void registrationReturnsFieldProblemDetails(String description, String body, String field, String fieldCode)
			throws Exception {
		HttpResponse<String> response = postRegistration(HttpClient.newHttpClient(), body, "application/json");

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(response.body())
			.contains("\"type\":\"urn:cloudforge:problem:iam:validation-failed\"",
					"\"title\":\"Registration validation failed\"", "\"status\":400", "\"instance\":\"/auth/register\"",
					"\"code\":\"IAM_VALIDATION_FAILED\"", "\"traceId\":\"", "\"field\":\"" + field + "\"",
					"\"code\":\"" + fieldCode + "\"")
			.doesNotContain("correct horse battery staple", "password_hash", "java.");
	}

	@Test
	void registrationRejectsUnsupportedMediaTypeWithProblemDetail() throws Exception {
		HttpResponse<String> response = postRegistration(HttpClient.newHttpClient(), "email=valid@example.com",
				"text/plain");

		assertThat(response.statusCode()).isEqualTo(415);
		assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(response.body()).contains("\"code\":\"IAM_UNSUPPORTED_MEDIA_TYPE\"", "\"traceId\":\"")
			.doesNotContain("email=valid@example.com");
	}

	@Test
	void registrationFailsClosedWithoutTrustedClientIp() throws Exception {
		HttpResponse<String> response = HttpClient.newHttpClient()
			.send(HttpRequest.newBuilder(uri("/auth/register"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{"))
				.build(), HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(503);
		assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(response.body()).contains("\"code\":\"PLATFORM_DEPENDENCY_UNAVAILABLE\"", "\"traceId\":\"")
			.doesNotContain("X-CloudForge-Client-IP");

		HttpResponse<String> invalid = postRegistration(HttpClient.newHttpClient(), "{", "application/json",
				"attacker.example");
		assertThat(invalid.statusCode()).isEqualTo(503);
		assertThat(invalid.body()).contains("\"code\":\"PLATFORM_DEPENDENCY_UNAVAILABLE\"")
			.doesNotContain("attacker.example");
	}

	@Test
	void malformedRegistrationConsumesOnlySourceBurstLimit() throws Exception {
		String clientIp = "198.51.100.77";
		for (int attempt = 0; attempt < 5; attempt++) {
			HttpResponse<String> invalid = postRegistration(HttpClient.newHttpClient(), "{", "application/json",
					clientIp);
			assertThat(invalid.statusCode()).isEqualTo(400);
		}

		HttpResponse<String> limited = postRegistration(HttpClient.newHttpClient(), "{", "application/json", clientIp);

		assertThat(limited.statusCode()).isEqualTo(429);
		assertThat(limited.headers().firstValue("Retry-After")).hasValue("12");
		assertThat(limited.body())
			.contains("\"code\":\"IAM_REGISTRATION_RATE_LIMITED\"", "\"retryAfterSeconds\":12", "\"traceId\":\"")
			.doesNotContain(clientIp);
	}

	@Test
	void parseableEmailConsumesEmailLimitBeforePasswordValidationAndCreatesExpiringHashedState() throws Exception {
		String email = "email-limit@example.com";
		String invalidRegistration = """
				{"email":"%s",
				 "password":42,
				 "confirmPassword":"short"}
				""".formatted(email);
		for (int attempt = 0; attempt < 10; attempt++) {
			HttpResponse<String> invalid = postRegistration(HttpClient.newHttpClient(), invalidRegistration,
					"application/json");
			assertThat(invalid.statusCode()).isEqualTo(400);
		}

		HttpResponse<String> limited = postRegistration(HttpClient.newHttpClient(), invalidRegistration,
				"application/json");

		assertThat(limited.statusCode()).isEqualTo(429);
		assertThat(limited.headers().firstValue("Retry-After")).hasValue("360");
		assertThat(limited.body())
			.contains("\"code\":\"IAM_REGISTRATION_RATE_LIMITED\"", "\"retryAfterSeconds\":360", "\"traceId\":\"")
			.doesNotContain(email, "short", "\"password\":42");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email))
			.isZero();
		Set<String> keys = this.redis.keys("cloudforge:local:iam:registration-rate-limit:*");
		assertThat(keys).isNotEmpty().noneMatch(key -> key.contains(email) || key.contains("198.51.100.77"));
		assertThat(keys)
			.allSatisfy(key -> assertThat(this.redis.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 3600L));
	}

	@Test
	void concurrentCaseVariantRegistrationsUseDatabaseUniqueConstraintAsFinalDecision() throws Exception {
		String password = "correct horse battery staple";
		String lowerCaseRequest = """
				{"email":"concurrent@example.com",
				 "password":"%s",
				 "confirmPassword":"%s"}
				""".formatted(password, password);
		String upperCaseRequest = """
				{"email":"Concurrent@Example.COM",
				 "password":"%s",
				 "confirmPassword":"%s"}
				""".formatted(password, password);
		CyclicBarrier start = new CyclicBarrier(2);

		HttpResponse<String> first;
		HttpResponse<String> second;
		try (ExecutorService requests = Executors.newFixedThreadPool(2)) {
			Future<HttpResponse<String>> firstRequest = requests
				.submit(() -> postRegistrationAfter(start, lowerCaseRequest));
			Future<HttpResponse<String>> secondRequest = requests
				.submit(() -> postRegistrationAfter(start, upperCaseRequest));
			first = firstRequest.get();
			second = secondRequest.get();
		}

		assertThat(List.of(first.statusCode(), second.statusCode())).containsExactlyInAnyOrder(201, 409);
		HttpResponse<String> conflict = first.statusCode() == 409 ? first : second;
		assertThat(conflict.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(conflict.body()).contains("\"code\":\"IAM_EMAIL_ALREADY_REGISTERED\"", "\"traceId\":\"")
			.doesNotContain(password, "users_email_key", "SQLException");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class,
				"concurrent@example.com"))
			.isEqualTo(1);
	}

	@Test
	void registrationWithValidSessionReturnsConflictBeforeCredentialValidation() throws Exception {
		CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
		String password = "correct horse battery staple";
		HttpResponse<String> initial = postRegistration(client, """
				{"email":"authenticated@example.com",
				 "password":"%s",
				 "confirmPassword":"%s",
				 "futureField":true}
				""".formatted(password, password), "application/json");
		assertThat(initial.statusCode()).isEqualTo(201);

		HttpResponse<String> conflict = postRegistration(client, """
				{"email":"not-an-email",
				 "password":"short",
				 "confirmPassword":"different"}
				""", "application/json");

		assertThat(conflict.statusCode()).isEqualTo(409);
		assertThat(conflict.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(conflict.body()).contains("\"code\":\"IAM_ALREADY_AUTHENTICATED\"", "\"traceId\":\"")
			.doesNotContain("not-an-email", "short", "different", "IAM_VALIDATION_FAILED");
		HttpResponse<String> malformedConflict = postRegistration(client, "{", "application/json");
		assertThat(malformedConflict.statusCode()).isEqualTo(409);
		assertThat(malformedConflict.body()).contains("\"code\":\"IAM_ALREADY_AUTHENTICATED\"")
			.doesNotContain("IAM_REQUEST_BODY_INVALID");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class,
				"authenticated@example.com"))
			.isEqualTo(1);
		HttpResponse<String> profile = client.send(HttpRequest.newBuilder(uri("/user/profile")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		assertThat(profile.statusCode()).isEqualTo(200);
		assertThat(profile.body()).contains("\"email\":\"authenticated@example.com\"");
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

	private HttpResponse<String> postRegistration(HttpClient client, String body, String contentType) throws Exception {
		return postRegistration(client, body, contentType, nextClientIp());
	}

	private HttpResponse<String> postRegistration(HttpClient client, String body, String contentType, String clientIp)
			throws Exception {
		return client.send(HttpRequest.newBuilder(uri("/auth/register"))
			.header("Content-Type", contentType)
			.header("X-CloudForge-Client-IP", clientIp)
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> postRegistrationAfter(CyclicBarrier start, String body) throws Exception {
		start.await();
		return postRegistration(HttpClient.newHttpClient(), body, "application/json");
	}

	private HttpResponse<String> postLogin(HttpClient client, String body) throws Exception {
		return client.send(HttpRequest.newBuilder(uri("/auth/login"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static String nextClientIp() {
		return "192.0.2." + CLIENT_IP_SEQUENCE.updateAndGet(value -> value % 250 + 1);
	}

	private static Stream<Arguments> invalidRegistrationRequests() {
		return Stream.of(Arguments.of("body must be an object", "[]", "body", "IAM_REQUEST_BODY_INVALID"),
				Arguments.of("malformed JSON", "{", "body", "IAM_REQUEST_BODY_INVALID"),
				Arguments.of("email is required", """
						{"password":"correct horse battery staple",
						 "confirmPassword":"correct horse battery staple"}
						""", "email", "IAM_FIELD_REQUIRED"), Arguments.of("email must be a string", """
						{"email":42,
						 "password":"correct horse battery staple",
						 "confirmPassword":"correct horse battery staple"}
						""", "email", "IAM_FIELD_TYPE_INVALID"), Arguments.of("email must be valid ASCII", """
						{"email":"usér@example.com",
						 "password":"correct horse battery staple",
						 "confirmPassword":"correct horse battery staple"}
						""", "email", "IAM_EMAIL_INVALID"), Arguments.of("password must be a string", """
						{"email":"valid@example.com",
						 "password":42,
						 "confirmPassword":"correct horse battery staple"}
						""", "password", "IAM_FIELD_TYPE_INVALID"), Arguments.of("password must meet its boundary", """
						{"email":"valid@example.com",
						 "password":"too short",
						 "confirmPassword":"too short"}
						""", "password", "IAM_PASSWORD_LENGTH_INVALID"), Arguments.of("confirmation is required", """
						{"email":"valid@example.com",
						 "password":"correct horse battery staple"}
						""", "confirmPassword", "IAM_FIELD_REQUIRED"), Arguments.of("confirmation must match", """
						{"email":"valid@example.com",
						 "password":"correct horse battery staple",
						 "confirmPassword":"different horse battery staple"}
						""", "confirmPassword", "IAM_PASSWORD_CONFIRMATION_MISMATCH"));
	}

	private static Stream<Arguments> invalidLoginRequests() {
		return Stream.of(Arguments.of("body must be an object", "[]", "body", "IAM_REQUEST_BODY_INVALID"),
				Arguments.of("password is required", """
						{"email":"valid@example.com"}
						""", "password", "IAM_FIELD_REQUIRED"), Arguments.of("email must be valid ASCII", """
						{"email":"usér@example.com",
						 "password":"correct horse battery staple"}
						""", "email", "IAM_EMAIL_INVALID"), Arguments.of("password must not exceed 128 code points", """
						{"email":"valid@example.com",
						 "password":"%s"}
						""".formatted("a".repeat(129)), "password", "IAM_PASSWORD_LENGTH_INVALID"));
	}

	private static void assertUnauthenticated(HttpResponse<String> response) {
		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(response.body()).contains("\"code\":\"SECURITY_UNAUTHENTICATED\"");
	}

	private static void assertSuccessfulLogin(HttpResponse<String> response) {
		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(response.body()).isEmpty();
		assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
		assertThat(response.headers().allValues("Set-Cookie"))
			.anySatisfy(value -> assertThat(value).startsWith("cloudforge_session=")
				.contains("Path=/", "Max-Age=604800", "HttpOnly", "SameSite=Lax"));
	}

	private static void assertInvalidCredentials(HttpResponse<String> response) {
		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
		assertThat(response.body())
			.contains("\"type\":\"urn:cloudforge:problem:iam:invalid-credentials\"",
					"\"title\":\"Invalid credentials\"", "\"code\":\"IAM_INVALID_CREDENTIALS\"", "\"traceId\":\"")
			.doesNotContain("credential-check@example.com", "missing@example.com", "wrong password phrase",
					"password_hash", "argon2");
	}

	private static String sessionCookie(CookieManager cookies) {
		return cookies.getCookieStore()
			.getCookies()
			.stream()
			.filter(cookie -> cookie.getName().equals("cloudforge_session"))
			.map(HttpCookie::getValue)
			.findFirst()
			.orElseThrow();
	}

	private static String withoutTraceId(String body) {
		return body.replaceAll("\"traceId\":\"[0-9a-f]{32}\"", "\"traceId\":\"\"");
	}

	private static String argon2Salt(String passwordHash) {
		int hashSeparator = passwordHash.lastIndexOf('$');
		int saltSeparator = passwordHash.lastIndexOf('$', hashSeparator - 1);
		return passwordHash.substring(saltSeparator + 1, hashSeparator);
	}

}
