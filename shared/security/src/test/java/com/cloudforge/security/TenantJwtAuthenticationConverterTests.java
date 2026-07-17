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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class TenantJwtAuthenticationConverterTests {

	@Test
	void mapsPermissionCodesWithoutChangingTheirNames() {
		Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
				Map.of("sub", "user-1", "permissions", List.of("order:read", "order:refund")));

		TenantJwtAuthenticationConverter converter = new TenantJwtAuthenticationConverter();

		assertThat(converter.authorities(jwt)).extracting((authority) -> authority.getAuthority())
			.contains("order:read", "order:refund");
	}

}
