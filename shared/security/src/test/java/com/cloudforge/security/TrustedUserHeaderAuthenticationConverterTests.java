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

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustedUserHeaderAuthenticationConverterTests {

	private static final String USER_ID = "018f0c24-7a00-7000-8000-000000000001";

	private final TrustedUserHeaderAuthenticationConverter converter = new TrustedUserHeaderAuthenticationConverter();

	@Test
	void convertsCanonicalUserIdIntoAnAuthenticatedPrincipalWithNoAuthorizationState() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(TrustedUserHeaderAuthenticationConverter.USER_ID_HEADER, USER_ID);

		Authentication authentication = this.converter.convert(request);

		assertThat(authentication).isNotNull();
		assertThat(authentication.isAuthenticated()).isTrue();
		assertThat(authentication.getPrincipal()).isEqualTo(new SessionUser(UUID.fromString(USER_ID)));
		assertThat(authentication.getCredentials()).isNull();
		assertThat(authentication.getAuthorities()).isEmpty();
		assertThat(SessionUser.class.getRecordComponents()).extracting(component -> component.getName())
			.containsExactly("userId");
	}

	@Test
	void returnsNoAuthenticationWhenTheTrustedHeaderIsAbsent() {
		assertThat(this.converter.convert(new MockHttpServletRequest())).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "not-a-uuid", "018F0C24-7A00-7000-8000-000000000001", "018f0c247a0070008000000000000001" })
	void rejectsAnyNonCanonicalUserId(String value) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(TrustedUserHeaderAuthenticationConverter.USER_ID_HEADER, value);

		assertThatThrownBy(() -> this.converter.convert(request)).isInstanceOf(BadCredentialsException.class);
	}

}
