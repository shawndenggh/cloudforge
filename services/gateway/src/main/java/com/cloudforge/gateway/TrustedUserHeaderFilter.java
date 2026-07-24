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
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import com.cloudforge.security.SessionUser;
import com.cloudforge.security.TrustedUserHeaderAuthenticationConverter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.Nullable;

import org.springframework.web.filter.OncePerRequestFilter;

final class TrustedUserHeaderFilter extends OncePerRequestFilter {

	private static final String PROFILE_PATH = "/api/v1/iam/user/profile";

	private final SessionExpiryFilter sessionExpiryFilter;

	TrustedUserHeaderFilter(SessionExpiryFilter sessionExpiryFilter) {
		this.sessionExpiryFilter = sessionExpiryFilter;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		SessionUser sessionUser = sessionUser(session);
		if (request.getRequestURI().equals(PROFILE_PATH) && sessionUser == null) {
			if (session != null) {
				session.invalidate();
			}
			this.sessionExpiryFilter.reject(request, response);
			return;
		}
		filterChain.doFilter(new TrustedUserRequest(request, sessionUser), response);
	}

	private static @Nullable SessionUser sessionUser(@Nullable HttpSession session) {
		if (session == null) {
			return null;
		}
		Object value = session.getAttribute(SessionUser.SESSION_ATTRIBUTE);
		if (!(value instanceof String userId)) {
			return null;
		}
		try {
			return SessionUser.parse(userId);
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static final class TrustedUserRequest extends HttpServletRequestWrapper {

		private final @Nullable String userId;

		TrustedUserRequest(HttpServletRequest request, @Nullable SessionUser sessionUser) {
			super(request);
			this.userId = sessionUser == null ? null : sessionUser.userId().toString();
		}

		@Override
		public @Nullable String getHeader(String name) {
			return isUserIdHeader(name) ? this.userId : super.getHeader(name);
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			if (!isUserIdHeader(name)) {
				return super.getHeaders(name);
			}
			return this.userId == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(this.userId));
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			List<String> names = Collections.list(super.getHeaderNames())
				.stream()
				.filter(name -> !isUserIdHeader(name))
				.collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
			if (this.userId != null) {
				names.add(TrustedUserHeaderAuthenticationConverter.USER_ID_HEADER);
			}
			return Collections.enumeration(names);
		}

		private static boolean isUserIdHeader(String name) {
			return TrustedUserHeaderAuthenticationConverter.USER_ID_HEADER.equalsIgnoreCase(name);
		}

	}

}
