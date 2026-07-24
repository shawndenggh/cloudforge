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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cloudforge.iam.identity.EmailAlreadyRegisteredException;
import com.cloudforge.iam.identity.IdentitySessionRevocationUnavailableException;
import com.cloudforge.iam.identity.IdentityValidationException;
import com.cloudforge.iam.identity.InvalidCredentialsException;
import com.cloudforge.iam.identity.LoginRateLimitUnavailableException;
import com.cloudforge.iam.identity.LoginRateLimitedException;
import com.cloudforge.iam.identity.LoginSessionUnavailableException;
import com.cloudforge.iam.identity.RegistrationRateLimitedException;
import com.cloudforge.iam.identity.RegistrationRateLimitUnavailableException;
import com.cloudforge.iam.identity.RegistrationSessionUnavailableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
final class IdentityErrorHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(IdentityErrorHandler.class);

	private static final Pattern TRACE_PARENT = Pattern
		.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");

	private static final Map<String, String> FIELD_ERROR_DETAILS = Map.of("IAM_REQUEST_BODY_INVALID",
			"Request body must be a JSON object.", "IAM_FIELD_REQUIRED", "A required string field is missing.",
			"IAM_FIELD_TYPE_INVALID", "Field must be a string.", "IAM_EMAIL_INVALID",
			"Email must be a valid ASCII email address.", "IAM_PASSWORD_LENGTH_INVALID",
			"Password must contain 15 to 128 Unicode code points.", "IAM_PASSWORD_CONTROL_CHARACTER",
			"Password must not contain control characters.", "IAM_PASSWORD_CONFIRMATION_MISMATCH",
			"Password confirmation must match the password.");

	private final Counter registrationSessionFailures;

	IdentityErrorHandler(MeterRegistry meterRegistry) {
		this.registrationSessionFailures = Counter.builder("cloudforge.iam.registration.session.creation.failures")
			.description("Registrations committed without a successfully created session")
			.register(meterRegistry);
	}

	@ExceptionHandler(UnauthenticatedException.class)
	ProblemDetail unauthenticated() {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
				"An authenticated user session is required.");
		problem.setTitle("Unauthenticated");
		problem.setProperty("code", "SECURITY_UNAUTHENTICATED");
		return problem;
	}

	@ExceptionHandler(IdentityValidationException.class)
	ProblemDetail validation(IdentityValidationException exception, HttpServletRequest request) {
		boolean login = request.getRequestURI().equals("/auth/login");
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				login ? "One or more login fields are invalid." : "One or more registration fields are invalid.");
		problem.setTitle(login ? "Login validation failed" : "Registration validation failed");
		problem.setProperty("code", "IAM_VALIDATION_FAILED");
		problem.setProperty("errors",
				exception.errors()
					.stream()
					.map(error -> new ProblemFieldError(error.field(), error.code(),
							FIELD_ERROR_DETAILS.getOrDefault(error.code(), "Field is invalid.")))
					.toList());
		complete(problem, request, "iam", "validation-failed");
		return problem;
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail unreadableBody(HttpServletRequest request) {
		return validation(
				new IdentityValidationException(
						List.of(new IdentityValidationException.FieldError("body", "IAM_REQUEST_BODY_INVALID"))),
				request);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ProblemDetail unsupportedMediaType(HttpServletRequest request) {
		boolean login = request.getRequestURI().equals("/auth/login");
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
				(login ? "Login" : "Registration") + " requires Content-Type application/json.");
		problem.setTitle("Unsupported media type");
		problem.setProperty("code", "IAM_UNSUPPORTED_MEDIA_TYPE");
		complete(problem, request, "iam", "unsupported-media-type");
		return problem;
	}

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	ProblemDetail emailAlreadyRegistered(HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"The login email is already registered.");
		problem.setTitle("Email already registered");
		problem.setProperty("code", "IAM_EMAIL_ALREADY_REGISTERED");
		complete(problem, request, "iam", "email-already-registered");
		return problem;
	}

	@ExceptionHandler(AlreadyAuthenticatedException.class)
	ProblemDetail alreadyAuthenticated(HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"Sign out before starting another authentication flow.");
		problem.setTitle("Already authenticated");
		problem.setProperty("code", "IAM_ALREADY_AUTHENTICATED");
		complete(problem, request, "iam", "already-authenticated");
		return problem;
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ProblemDetail invalidCredentials(HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
				"The login email or password is incorrect.");
		problem.setTitle("Invalid credentials");
		problem.setProperty("code", "IAM_INVALID_CREDENTIALS");
		complete(problem, request, "iam", "invalid-credentials");
		return problem;
	}

	@ExceptionHandler(LoginSessionUnavailableException.class)
	ProblemDetail loginSessionUnavailable(HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"The authenticated session could not be created.");
		problem.setTitle("Session unavailable");
		problem.setProperty("code", "IAM_SESSION_UNAVAILABLE");
		complete(problem, request, "iam", "session-unavailable");
		return problem;
	}

	@ExceptionHandler(IdentitySessionRevocationUnavailableException.class)
	ProblemDetail sessionRevocationUnavailable(HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"The current session could not be safely revoked.");
		problem.setTitle("Session revocation unavailable");
		problem.setProperty("code", "IAM_SESSION_REVOCATION_UNAVAILABLE");
		complete(problem, request, "iam", "session-revocation-unavailable");
		return problem;
	}

	@ExceptionHandler(LoginRateLimitedException.class)
	ResponseEntity<ProblemDetail> loginRateLimited(LoginRateLimitedException exception, HttpServletRequest request) {
		long retryAfterSeconds = Math.ceilDiv(exception.retryAfter().toMillis(), 1_000);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
				"Login attempts are temporarily limited.");
		problem.setTitle("Login rate limited");
		problem.setProperty("code", "IAM_LOGIN_RATE_LIMITED");
		problem.setProperty("retryAfterSeconds", retryAfterSeconds);
		complete(problem, request, "iam", "login-rate-limited");
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
			.header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
			.body(problem);
	}

	@ExceptionHandler(LoginRateLimitUnavailableException.class)
	ProblemDetail loginRateLimitUnavailable(HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"Login is temporarily unavailable.");
		problem.setTitle("Dependency unavailable");
		problem.setProperty("code", "PLATFORM_DEPENDENCY_UNAVAILABLE");
		complete(problem, request, "platform", "dependency-unavailable");
		return problem;
	}

	@ExceptionHandler(RegistrationRateLimitedException.class)
	ResponseEntity<ProblemDetail> registrationRateLimited(RegistrationRateLimitedException exception,
			HttpServletRequest request) {
		long retryAfterSeconds = Math.ceilDiv(exception.retryAfter().toMillis(), 1_000);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
				"Registration attempts are temporarily limited.");
		problem.setTitle("Registration rate limited");
		problem.setProperty("code", "IAM_REGISTRATION_RATE_LIMITED");
		problem.setProperty("retryAfterSeconds", retryAfterSeconds);
		complete(problem, request, "iam", "registration-rate-limited");
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
			.header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
			.body(problem);
	}

	@ExceptionHandler(RegistrationRateLimitUnavailableException.class)
	ProblemDetail registrationRateLimitUnavailable(HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"Registration is temporarily unavailable.");
		problem.setTitle("Dependency unavailable");
		problem.setProperty("code", "PLATFORM_DEPENDENCY_UNAVAILABLE");
		complete(problem, request, "platform", "dependency-unavailable");
		return problem;
	}

	@ExceptionHandler(RegistrationSessionUnavailableException.class)
	ProblemDetail registrationSessionUnavailable(HttpServletRequest request) {
		this.registrationSessionFailures.increment();
		LOGGER.warn("Registration committed but session creation remained unavailable after bounded retries");
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"Registration completed, but the authenticated session could not be created.");
		problem.setTitle("Registration session unavailable");
		problem.setProperty("code", "IAM_REGISTRATION_COMPLETED_SESSION_UNAVAILABLE");
		complete(problem, request, "iam", "registration-completed-session-unavailable");
		return problem;
	}

	private static void complete(ProblemDetail problem, HttpServletRequest request, String domain, String condition) {
		problem.setType(URI.create("urn:cloudforge:problem:" + domain + ":" + condition));
		problem.setInstance(URI.create(request.getRequestURI()));
		problem.setProperty("traceId", traceId(request));
	}

	private static String traceId(HttpServletRequest request) {
		String traceParent = request.getHeader("traceparent");
		if (traceParent != null) {
			Matcher matcher = TRACE_PARENT.matcher(traceParent);
			if (matcher.matches() && !matcher.group(1).equals("00000000000000000000000000000000")) {
				return matcher.group(1);
			}
		}
		return UUID.randomUUID().toString().replace("-", "");
	}

	private record ProblemFieldError(String field, String code, String detail) {
	}

}
