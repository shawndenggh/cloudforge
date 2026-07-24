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

import jakarta.servlet.http.HttpServletRequest;

final class TrustedClientIp {

	private static final String ATTRIBUTE = TrustedClientIp.class.getName();

	private TrustedClientIp() {
	}

	static String resolve(HttpServletRequest request) {
		String value = request.getHeader("X-CloudForge-Client-IP");
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Trusted client IP is missing");
		}
		try {
			if (isIpv4(value)) {
				String clientIp = InetAddress.getByName(value).getHostAddress();
				request.setAttribute(ATTRIBUTE, clientIp);
				return clientIp;
			}
			if (value.indexOf(':') >= 0 && value.chars()
				.allMatch(character -> Character.digit(character, 16) >= 0 || character == ':' || character == '.')) {
				InetAddress address = InetAddress.getByName(value);
				if (address instanceof Inet6Address) {
					String clientIp = address.getHostAddress();
					request.setAttribute(ATTRIBUTE, clientIp);
					return clientIp;
				}
			}
		}
		catch (UnknownHostException exception) {
			// Invalid trusted context is rejected below.
		}
		throw new IllegalArgumentException("Trusted client IP is invalid");
	}

	static String current(HttpServletRequest request) {
		Object clientIp = request.getAttribute(ATTRIBUTE);
		if (clientIp instanceof String value) {
			return value;
		}
		throw new IllegalStateException("Trusted client IP was not established");
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
