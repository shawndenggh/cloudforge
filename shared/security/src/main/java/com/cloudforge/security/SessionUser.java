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

/**
 * The only identity established by a valid CloudForge Session.
 *
 * @param userId the global User identifier
 */
public record SessionUser(UUID userId) {

	public static final String SESSION_ATTRIBUTE = "user_id";

	public SessionUser {
		if (userId == null) {
			throw new IllegalArgumentException("User ID is required");
		}
	}

	public static SessionUser parse(String value) {
		UUID userId = UUID.fromString(value);
		if (!userId.toString().equals(value)) {
			throw new IllegalArgumentException("User ID must be a canonical UUID");
		}
		return new SessionUser(userId);
	}

}
