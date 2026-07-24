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
package com.cloudforge.iam.identity;

import java.time.Duration;

public final class RegistrationRateLimitedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final transient Duration retryAfter;

	RegistrationRateLimitedException(Duration retryAfter) {
		super("Registration rate limit exceeded");
		this.retryAfter = retryAfter;
	}

	public Duration retryAfter() {
		return this.retryAfter;
	}

}
