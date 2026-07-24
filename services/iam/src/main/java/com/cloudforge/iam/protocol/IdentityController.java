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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.cloudforge.iam.identity.IdentityValidationException;
import com.cloudforge.iam.identity.IdentityValidationException.FieldError;
import com.cloudforge.iam.identity.IdentityModule;
import com.cloudforge.iam.identity.IdentityModule.Authentication;
import com.cloudforge.iam.identity.IdentityModule.LoginCommand;
import com.cloudforge.iam.identity.IdentityModule.RegisterCommand;
import com.cloudforge.iam.identity.IdentityModule.Registration;
import com.cloudforge.iam.identity.IdentityModule.UserProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import tools.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class IdentityController {

	private static final String USER_ID_ATTRIBUTE = "user_id";

	private static final Duration SESSION_COOKIE_MAX_AGE = Duration.ofDays(7);

	private final IdentityModule identities;

	private final CookieSerializer cookieSerializer;

	private final boolean secureCookies;

	IdentityController(IdentityModule identities, CookieSerializer cookieSerializer,
			@Value("${cloudforge.security.secure-cookies:true}") boolean secureCookies) {
		this.identities = identities;
		this.cookieSerializer = cookieSerializer;
		this.secureCookies = secureCookies;
	}

	@PostMapping(path = "/auth/register", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<Void> register(@RequestBody JsonNode body, HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) {
		checkParseableEmailRateLimit(body);
		RegistrationRequest request = registrationRequest(body);
		Registration registration = this.identities
			.register(new RegisterCommand(request.email(), request.password(), request.confirmPassword()));
		writeSessionCookie(servletRequest, servletResponse, registration.sessionId());
		expireCsrfCookie(servletResponse);
		return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).build();
	}

	@PostMapping(path = "/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<Void> login(@RequestBody JsonNode body, HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) {
		LoginRequest request = loginRequest(body);
		Authentication authentication = this.identities.login(new LoginCommand(request.email(), request.password()));
		writeSessionCookie(servletRequest, servletResponse, authentication.sessionId());
		expireCsrfCookie(servletResponse);
		return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).build();
	}

	private void writeSessionCookie(HttpServletRequest request, HttpServletResponse response, String sessionId) {
		CookieValue sessionCookie = new CookieValue(request, response, sessionId);
		sessionCookie.setCookieMaxAge(Math.toIntExact(SESSION_COOKIE_MAX_AGE.toSeconds()));
		this.cookieSerializer.writeCookieValue(sessionCookie);
	}

	private void expireCsrfCookie(HttpServletResponse response) {
		ResponseCookie expiredCsrfCookie = ResponseCookie.from("cloudforge_csrf_token", "")
			.path("/")
			.httpOnly(false)
			.secure(this.secureCookies)
			.sameSite("Lax")
			.maxAge(Duration.ZERO)
			.build();
		response.addHeader(HttpHeaders.SET_COOKIE, expiredCsrfCookie.toString());
	}

	@GetMapping("/user/profile")
	ResponseEntity<UserProfile> profile(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			throw new UnauthenticatedException();
		}

		Object userId = session.getAttribute(USER_ID_ATTRIBUTE);
		if (!(userId instanceof String value)) {
			session.invalidate();
			throw new UnauthenticatedException();
		}

		try {
			return this.identities.findUser(UUID.fromString(value))
				.map(profile -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profile))
				.orElseThrow(() -> {
					session.invalidate();
					return new UnauthenticatedException();
				});
		}
		catch (IllegalArgumentException exception) {
			session.invalidate();
			throw new UnauthenticatedException();
		}
	}

	private void checkParseableEmailRateLimit(JsonNode body) {
		if (!body.isObject()) {
			return;
		}
		JsonNode email = body.get("email");
		if (email != null && email.isString()) {
			this.identities.checkRegistrationEmail(email.stringValue());
		}
	}

	private static RegistrationRequest registrationRequest(JsonNode body) {
		if (!body.isObject()) {
			throw new IdentityValidationException(List.of(new FieldError("body", "IAM_REQUEST_BODY_INVALID")));
		}

		List<FieldError> errors = new ArrayList<>();
		String email = requiredString(body, "email", errors);
		String password = requiredString(body, "password", errors);
		String confirmPassword = requiredString(body, "confirmPassword", errors);
		if (!errors.isEmpty()) {
			throw new IdentityValidationException(errors);
		}
		return new RegistrationRequest(email, password, confirmPassword);
	}

	private static LoginRequest loginRequest(JsonNode body) {
		if (!body.isObject()) {
			throw new IdentityValidationException(List.of(new FieldError("body", "IAM_REQUEST_BODY_INVALID")));
		}

		List<FieldError> errors = new ArrayList<>();
		String email = requiredString(body, "email", errors);
		String password = requiredString(body, "password", errors);
		if (!errors.isEmpty()) {
			throw new IdentityValidationException(errors);
		}
		return new LoginRequest(email, password);
	}

	private static String requiredString(JsonNode body, String field, List<FieldError> errors) {
		JsonNode value = body.get(field);
		if (value == null || value.isNull()) {
			errors.add(new FieldError(field, "IAM_FIELD_REQUIRED"));
			return "";
		}
		if (!value.isString()) {
			errors.add(new FieldError(field, "IAM_FIELD_TYPE_INVALID"));
			return "";
		}
		return value.stringValue();
	}

	record RegistrationRequest(String email, String password, String confirmPassword) {
	}

	record LoginRequest(String email, String password) {
	}

}
