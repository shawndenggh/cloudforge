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

import java.util.Collection;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

public final class TenantJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();

	public TenantJwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter permissions = new JwtGrantedAuthoritiesConverter();
		permissions.setAuthoritiesClaimName("permissions");
		permissions.setAuthorityPrefix("");
		this.delegate.setJwtGrantedAuthoritiesConverter(permissions);
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		return this.delegate.convert(jwt);
	}

	Collection<GrantedAuthority> authorities(Jwt jwt) {
		return convert(jwt).getAuthorities();
	}

}
