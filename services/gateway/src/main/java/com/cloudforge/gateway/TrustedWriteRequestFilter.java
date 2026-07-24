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
import java.net.URI;
import java.util.Locale;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;

final class TrustedWriteRequestFilter extends OncePerRequestFilter {

	private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

	private final Set<String> allowedOrigins;

	private final AccessDeniedHandler accessDeniedHandler;

	TrustedWriteRequestFilter(Set<String> allowedOrigins, AccessDeniedHandler accessDeniedHandler) {
		this.allowedOrigins = allowedOrigins.stream()
			.map(TrustedWriteRequestFilter::normalizeOrigin)
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
		this.accessDeniedHandler = accessDeniedHandler;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!WRITE_METHODS.contains(request.getMethod())
				|| (hasTrustedSource(request) && !isExplicitlyCrossSite(request))) {
			filterChain.doFilter(request, response);
			return;
		}
		this.accessDeniedHandler.handle(request, response, new AccessDeniedException("Untrusted write request source"));
	}

	private static boolean isExplicitlyCrossSite(HttpServletRequest request) {
		return "cross-site".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site"));
	}

	private boolean hasTrustedSource(HttpServletRequest request) {
		String source = request.getHeader("Origin");
		if (source == null) {
			source = request.getHeader("Referer");
		}
		if (source == null) {
			return false;
		}
		try {
			String sourceOrigin = normalizeOrigin(source);
			return sourceOrigin.equals(requestOrigin(request)) || this.allowedOrigins.contains(sourceOrigin);
		}
		catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private static String requestOrigin(HttpServletRequest request) {
		int port = request.getServerPort();
		String scheme = request.getScheme().toLowerCase(Locale.ROOT);
		boolean defaultPort = (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
		return scheme + "://" + request.getServerName().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
	}

	private static String normalizeOrigin(String value) {
		URI uri = URI.create(value);
		String scheme = uri.getScheme();
		String host = uri.getHost();
		if (scheme == null || host == null || uri.getUserInfo() != null || uri.getFragment() != null
				|| uri.getQuery() != null) {
			throw new IllegalArgumentException("Origin must be an absolute HTTP origin");
		}
		String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
		if (!normalizedScheme.equals("http") && !normalizedScheme.equals("https")) {
			throw new IllegalArgumentException("Origin must use HTTP or HTTPS");
		}
		int port = uri.getPort();
		boolean defaultPort = (normalizedScheme.equals("http") && port == 80)
				|| (normalizedScheme.equals("https") && port == 443);
		return normalizedScheme + "://" + host.toLowerCase(Locale.ROOT)
				+ ((port == -1 || defaultPort) ? "" : ":" + port);
	}

}
