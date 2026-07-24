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
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import org.springframework.cloud.gateway.server.mvc.handler.GatewayServerResponse;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchange;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.function.ServerResponse;

final class IamProxyExchange implements ProxyExchange {

	private static final Pattern TRACEPARENT = Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");

	private static final Pattern TRACE_ID = Pattern.compile("^[0-9a-f]{32}$");

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

	IamProxyExchange(RestClient restClient, ObjectMapper objectMapper) {
		this.restClient = restClient;
		this.objectMapper = objectMapper;
	}

	@Override
	public ServerResponse exchange(Request request) {
		URI uri = Objects.requireNonNull(request.getUri(), "IAM URI must not be null");
		try {
			RestClient.RequestBodySpec requestSpec = this.restClient.method(request.getMethod())
				.uri(uri)
				.headers(headers -> headers.putAll(request.getHeaders()));
			if (hasBody(request)) {
				requestSpec.body(output -> copyRequestBody(request, output));
			}
			return requestSpec.exchange((clientRequest, clientResponse) -> proxyResponse(request, clientResponse),
					false);
		}
		catch (RestClientException | UncheckedIOException exception) {
			if (hasCause(exception, HttpTimeoutException.class) || hasCause(exception, SocketTimeoutException.class)) {
				return dependencyProblem(request, HttpStatus.GATEWAY_TIMEOUT, "IAM_DEPENDENCY_TIMEOUT",
						"IAM dependency timed out");
			}
			return dependencyProblem(request, HttpStatus.BAD_GATEWAY, "IAM_BAD_GATEWAY",
					"IAM dependency was unavailable");
		}
	}

	private ServerResponse proxyResponse(Request request, ClientHttpResponse clientResponse) throws IOException {
		try (clientResponse) {
			HttpStatusCode status = clientResponse.getStatusCode();
			HttpHeaders headers = HttpHeaders.copyOf(clientResponse.getHeaders());
			byte[] body = clientResponse.getBody().readAllBytes();
			if (status.isError() && !isValidProblem(status, headers.getContentType(), body)) {
				return dependencyProblem(request, HttpStatus.BAD_GATEWAY, "IAM_INVALID_RESPONSE",
						"IAM dependency returned an invalid error response");
			}
			ServerResponse serverResponse = exactBodyResponse(status, body);
			BufferedResponse response = new BufferedResponse(status, headers);
			request.getResponseConsumers()
				.forEach(responseConsumer -> responseConsumer.accept(response, serverResponse));
			return serverResponse;
		}
	}

	private boolean isValidProblem(HttpStatusCode status, @Nullable MediaType contentType, byte[] body) {
		if (contentType == null || !MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(contentType)) {
			return false;
		}
		try {
			JsonNode problem = this.objectMapper.readTree(body);
			return problem.isObject() && requiredText(problem, "type") && requiredText(problem, "title")
					&& problem.path("status").isInt() && problem.path("status").intValue() == status.value()
					&& requiredText(problem, "code") && requiredTraceId(problem)
					&& (problem.get("errors") == null || problem.get("errors").isArray());
		}
		catch (JacksonException exception) {
			return false;
		}
	}

	private static boolean requiredText(JsonNode problem, String field) {
		JsonNode value = problem.get(field);
		return value != null && value.isString() && !value.stringValue().isBlank();
	}

	private static boolean requiredTraceId(JsonNode problem) {
		JsonNode value = problem.get("traceId");
		return value != null && value.isString() && TRACE_ID.matcher(value.stringValue()).matches()
				&& !value.stringValue().equals("00000000000000000000000000000000");
	}

	private static ServerResponse exactBodyResponse(HttpStatusCode status, byte[] body) {
		return GatewayServerResponse.status(status).build((request, response) -> {
			response.getOutputStream().write(body);
			return null;
		});
	}

	private static ServerResponse dependencyProblem(Request request, HttpStatus status, String code, String detail) {
		String body = "{\"type\":\"urn:cloudforge:problem:iam:" + problemType(code) + "\",\"title\":\""
				+ status.getReasonPhrase() + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail
				+ "\",\"code\":\"" + code + "\",\"traceId\":\"" + traceId(request) + "\"}";
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		return GatewayServerResponse.status(status)
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.contentLength(bytes.length)
			.build((serverRequest, response) -> {
				response.getOutputStream().write(bytes);
				return null;
			});
	}

	private static String problemType(String code) {
		return code.substring("IAM_".length()).toLowerCase(java.util.Locale.ROOT).replace('_', '-');
	}

	private static String traceId(Request request) {
		String traceparent = request.getServerRequest().headers().firstHeader("traceparent");
		if (traceparent != null) {
			Matcher matcher = TRACEPARENT.matcher(traceparent);
			if (matcher.matches() && !matcher.group(1).equals("00000000000000000000000000000000")) {
				return matcher.group(1);
			}
		}
		return UUID.randomUUID().toString().replace("-", "");
	}

	private static boolean hasBody(Request request) {
		try {
			return !request.getServerRequest().servletRequest().getInputStream().isFinished();
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private static int copyRequestBody(Request request, OutputStream output) throws IOException {
		return StreamUtils.copy(request.getServerRequest().servletRequest().getInputStream(), output);
	}

	private static boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
		Throwable current = throwable;
		while (current != null) {
			if (causeType.isInstance(current)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private record BufferedResponse(HttpStatusCode statusCode, HttpHeaders headers) implements Response {

		@Override
		public HttpStatusCode getStatusCode() {
			return this.statusCode;
		}

		@Override
		public HttpHeaders getHeaders() {
			return this.headers;
		}

	}

}
