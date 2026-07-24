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

import java.util.UUID;

import com.cloudforge.iam.identity.IdentityModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.web.servlet.HandlerInterceptor;

final class RegistrationAuthenticationInterceptor implements HandlerInterceptor {

	private static final String USER_ID_ATTRIBUTE = "user_id";

	private final IdentityModule identities;

	RegistrationAuthenticationInterceptor(IdentityModule identities) {
		this.identities = identities;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			return true;
		}

		Object userId = session.getAttribute(USER_ID_ATTRIBUTE);
		if (!(userId instanceof String value)) {
			session.invalidate();
			return true;
		}
		try {
			if (this.identities.findUser(UUID.fromString(value)).isPresent()) {
				throw new AlreadyAuthenticatedException();
			}
		}
		catch (IllegalArgumentException exception) {
			// Invalid identity attributes are treated as an anonymous request.
		}
		session.invalidate();
		return true;
	}

}
