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

import java.time.Clock;
import java.time.Duration;
import java.util.List;

public final class DefaultRegistrationRateLimiter implements RegistrationRateLimiter {

	private static final int SOURCE_BURST_CAPACITY = 5;

	private static final int SOURCE_HOURLY_CAPACITY = 20;

	private static final int EMAIL_HOURLY_CAPACITY = 10;

	private static final Duration SOURCE_BURST_REFILL_INTERVAL = Duration.ofSeconds(12);

	private static final Duration SOURCE_HOURLY_REFILL_INTERVAL = Duration.ofMinutes(3);

	private static final Duration EMAIL_HOURLY_REFILL_INTERVAL = Duration.ofMinutes(6);

	private final RegistrationRateLimitStore store;

	private final Clock clock;

	public DefaultRegistrationRateLimiter(RegistrationRateLimitStore store, Clock clock) {
		this.store = store;
		this.clock = clock;
	}

	@Override
	public void checkSource(String clientIp) {
		check(List.of(new TokenBucket("source:burst:" + clientIp, SOURCE_BURST_CAPACITY, SOURCE_BURST_REFILL_INTERVAL),
				new TokenBucket("source:hour:" + clientIp, SOURCE_HOURLY_CAPACITY, SOURCE_HOURLY_REFILL_INTERVAL)));
	}

	@Override
	public void checkEmail(String normalizedEmail) {
		check(List
			.of(new TokenBucket("email:hour:" + normalizedEmail, EMAIL_HOURLY_CAPACITY, EMAIL_HOURLY_REFILL_INTERVAL)));
	}

	private void check(List<TokenBucket> buckets) {
		Duration retryAfter = this.store.consume(buckets, this.clock.instant());
		if (!retryAfter.isZero()) {
			throw new RegistrationRateLimitedException(retryAfter);
		}
	}

}
