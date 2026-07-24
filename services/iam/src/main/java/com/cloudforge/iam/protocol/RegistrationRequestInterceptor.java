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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import com.cloudforge.iam.identity.IdentityModule;
import com.cloudforge.iam.identity.RegistrationRateLimitUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.web.servlet.HandlerInterceptor;

final class RegistrationRequestInterceptor implements HandlerInterceptor {

	private static final String USER_ID_ATTRIBUTE = "user_id";

	private final IdentityModule identities;

	RegistrationRequestInterceptor(IdentityModule identities) {
		this.identities = identities;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			checkSource(request);
			return true;
		}

		Object userId = session.getAttribute(USER_ID_ATTRIBUTE);
		if (!(userId instanceof String value)) {
			session.invalidate();
			checkSource(request);
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
		checkSource(request);
		return true;
	}

	private void checkSource(HttpServletRequest request) {
		this.identities.checkRegistrationSource(trustedClientIp(request));
	}

	private static String trustedClientIp(HttpServletRequest request) {
		String value = request.getHeader("X-CloudForge-Client-IP");
		if (value == null || value.isBlank()) {
			throw new RegistrationRateLimitUnavailableException();
		}
		try {
			if (isIpv4(value)) {
				return InetAddress.getByName(value).getHostAddress();
			}
			if (value.indexOf(':') >= 0 && value.chars()
				.allMatch(character -> Character.digit(character, 16) >= 0 || character == ':' || character == '.')) {
				InetAddress address = InetAddress.getByName(value);
				if (address instanceof Inet6Address) {
					return address.getHostAddress();
				}
			}
		}
		catch (UnknownHostException exception) {
			// Invalid trusted context fails closed below.
		}
		throw new RegistrationRateLimitUnavailableException();
	}

	private static boolean isIpv4(String value) {
		String[] parts = value.split("\\.", -1);
		if (parts.length != 4) {
			return false;
		}
		for (String part : parts) {
			if (part.isEmpty() || part.length() > 3
					|| !part.chars().allMatch(character -> character >= '0' && character <= '9')) {
				return false;
			}
			int octet = Integer.parseInt(part);
			if (octet > 255) {
				return false;
			}
		}
		return true;
	}

}
