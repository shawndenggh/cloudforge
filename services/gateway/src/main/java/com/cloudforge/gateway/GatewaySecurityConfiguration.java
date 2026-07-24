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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RouterFunctions.route;

@Configuration(proxyBeanMethods = false)
class GatewaySecurityConfiguration {

	@Bean
	SecurityFilterChain gatewaySecurityFilterChain(HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository,
			AccessDeniedHandler csrfAccessDeniedHandler, TrustedWriteRequestFilter trustedWriteRequestFilter,
			CorsConfigurationSource corsConfigurationSource) throws Exception {
		CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
		http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
			.cors(cors -> cors.configurationSource(corsConfigurationSource))
			.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository).csrfTokenRequestHandler(requestHandler))
			.addFilterBefore(trustedWriteRequestFilter, CorsFilter.class)
			.exceptionHandling(exceptions -> exceptions.accessDeniedHandler(csrfAccessDeniedHandler));
		return http.build();
	}

	@Bean
	FilterRegistrationBean<TrustedClientIpHeaderFilter> trustedClientIpHeaderFilter() {
		FilterRegistrationBean<TrustedClientIpHeaderFilter> registration = new FilterRegistrationBean<>();
		registration.setFilter(new TrustedClientIpHeaderFilter());
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}

	@Bean
	AccessDeniedHandler csrfAccessDeniedHandler() {
		return (request, response, exception) -> {
			byte[] body = ("{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
					+ "\"detail\":\"CSRF validation failed\",\"code\":\"SECURITY_CSRF_INVALID\"}")
				.getBytes(StandardCharsets.UTF_8);
			response.setStatus(HttpStatus.FORBIDDEN.value());
			response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			response.setContentLength(body.length);
			response.getOutputStream().write(body);
		};
	}

	@Bean
	TrustedWriteRequestFilter trustedWriteRequestFilter(Environment environment,
			@Value("${cloudforge.security.allowed-origins:}") String configuredOrigins,
			AccessDeniedHandler csrfAccessDeniedHandler) {
		Set<String> allowedOrigins = localAllowedOrigins(environment, configuredOrigins);
		return new TrustedWriteRequestFilter(allowedOrigins, csrfAccessDeniedHandler);
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(Environment environment,
			@Value("${cloudforge.security.allowed-origins:}") String configuredOrigins) {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		if (!environment.acceptsProfiles(Profiles.of("local"))) {
			return source;
		}
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(List.copyOf(localAllowedOrigins(environment, configuredOrigins)));
		configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Content-Type", "X-CloudForge-CSRF", "traceparent"));
		configuration.setExposedHeaders(List.of("Retry-After"));
		configuration.setAllowCredentials(true);
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}

	private static Set<String> localAllowedOrigins(Environment environment, String configuredOrigins) {
		if (!environment.acceptsProfiles(Profiles.of("local"))) {
			return Set.of();
		}
		return Arrays.stream(configuredOrigins.split(","))
			.map(String::trim)
			.filter(origin -> !origin.isEmpty())
			.collect(Collectors.toUnmodifiableSet());
	}

	@Bean
	CookieCsrfTokenRepository csrfTokenRepository(
			@Value("${cloudforge.security.secure-cookies:true}") boolean secureCookies) {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieName("cloudforge_csrf_token");
		repository.setHeaderName("X-CloudForge-CSRF");
		repository
			.setCookieCustomizer(cookie -> cookie.path("/").secure(secureCookies).httpOnly(false).sameSite("Lax"));
		return repository;
	}

	@Bean
	RouterFunction<ServerResponse> csrfRoute() {
		return route(GET("/api/v1/iam/csrf"), request -> {
			CsrfToken csrfToken = (CsrfToken) request.servletRequest().getAttribute(CsrfToken.class.getName());
			csrfToken.getToken();
			return ServerResponse.noContent().cacheControl(CacheControl.noStore()).build();
		});
	}

}
