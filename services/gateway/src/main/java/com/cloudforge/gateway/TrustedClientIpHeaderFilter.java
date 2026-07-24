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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

final class TrustedClientIpHeaderFilter extends OncePerRequestFilter {

	static final String CLIENT_IP_HEADER = "X-CloudForge-Client-IP";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		filterChain.doFilter(new TrustedClientIpRequest(request, normalizedRemoteAddress(request)), response);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/v1/iam/");
	}

	private static String normalizedRemoteAddress(HttpServletRequest request) throws ServletException {
		try {
			return InetAddress.getByName(request.getRemoteAddr()).getHostAddress();
		}
		catch (UnknownHostException exception) {
			throw new ServletException("Container supplied an invalid remote address", exception);
		}
	}

	private static final class TrustedClientIpRequest extends HttpServletRequestWrapper {

		private final String clientIp;

		TrustedClientIpRequest(HttpServletRequest request, String clientIp) {
			super(request);
			this.clientIp = clientIp;
		}

		@Override
		public String getHeader(String name) {
			return CLIENT_IP_HEADER.equalsIgnoreCase(name) ? this.clientIp : super.getHeader(name);
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			return CLIENT_IP_HEADER.equalsIgnoreCase(name) ? Collections.enumeration(List.of(this.clientIp))
					: super.getHeaders(name);
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			List<String> names = Collections.list(super.getHeaderNames())
				.stream()
				.filter(name -> !CLIENT_IP_HEADER.equalsIgnoreCase(name))
				.collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
			names.add(CLIENT_IP_HEADER);
			return Collections.enumeration(names);
		}

	}

}
