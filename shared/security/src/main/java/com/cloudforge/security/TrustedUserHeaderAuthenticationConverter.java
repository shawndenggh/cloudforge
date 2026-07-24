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
package com.cloudforge.security;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * Adapts the User header established by Gateway into a minimal Spring Security principal.
 *
 * <p>
 * Applications must enable this adapter only on private listeners that accept traffic
 * exclusively from Gateway.
 */
public final class TrustedUserHeaderAuthenticationConverter implements AuthenticationConverter {

	public static final String USER_ID_HEADER = "X-CloudForge-User-Id";

	@Override
	public @Nullable Authentication convert(HttpServletRequest request) {
		Enumeration<String> values = request.getHeaders(USER_ID_HEADER);
		if (values == null) {
			values = Collections.emptyEnumeration();
		}
		if (!values.hasMoreElements()) {
			return null;
		}
		String value = values.nextElement();
		if (values.hasMoreElements()) {
			throw new BadCredentialsException("Trusted User header must occur exactly once");
		}
		try {
			SessionUser user = SessionUser.parse(value);
			return UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
		}
		catch (IllegalArgumentException exception) {
			throw new BadCredentialsException("Trusted User header must contain a canonical UUID", exception);
		}
	}

}
