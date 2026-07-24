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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.web.filter.OncePerRequestFilter;

final class SessionExpiryFilter extends OncePerRequestFilter {

	private static final Duration ABSOLUTE_TIMEOUT = Duration.ofDays(7);

	private static final String SESSION_COOKIE = "cloudforge_session";

	private static final String PROFILE_PATH = "/api/v1/iam/user/profile";

	private final Clock clock;

	private final CookieSerializer cookieSerializer;

	SessionExpiryFilter(Clock clock, CookieSerializer cookieSerializer) {
		this.clock = clock;
		this.cookieSerializer = cookieSerializer;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session != null) {
			Instant absoluteDeadline = Instant.ofEpochMilli(session.getCreationTime()).plus(ABSOLUTE_TIMEOUT);
			if (!this.clock.instant().isBefore(absoluteDeadline)) {
				session.invalidate();
				clearSessionCookie(request, response);
				if (request.getRequestURI().equals(PROFILE_PATH)) {
					writeUnauthenticated(response);
					return;
				}
				filterChain.doFilter(request, response);
				return;
			}
		}
		else if (request.getRequestURI().equals(PROFILE_PATH) && hasSessionCookie(request)) {
			reject(request, response);
			return;
		}
		filterChain.doFilter(request, response);
	}

	void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
		clearSessionCookie(request, response);
		writeUnauthenticated(response);
	}

	private void clearSessionCookie(HttpServletRequest request, HttpServletResponse response) {
		CookieValue expiredSession = new CookieValue(request, response, "");
		expiredSession.setCookieMaxAge(0);
		this.cookieSerializer.writeCookieValue(expiredSession);
	}

	private static void writeUnauthenticated(HttpServletResponse response) throws IOException {
		byte[] body = ("{\"type\":\"urn:cloudforge:problem:security:unauthenticated\","
				+ "\"title\":\"Unauthenticated\",\"status\":401,"
				+ "\"detail\":\"An authenticated user session is required.\","
				+ "\"code\":\"SECURITY_UNAUTHENTICATED\"}")
			.getBytes(StandardCharsets.UTF_8);
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setContentLength(body.length);
		response.getOutputStream().write(body);
	}

	private static boolean hasSessionCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return false;
		}
		for (Cookie cookie : cookies) {
			if (cookie.getName().equals(SESSION_COOKIE)) {
				return true;
			}
		}
		return false;
	}

}
