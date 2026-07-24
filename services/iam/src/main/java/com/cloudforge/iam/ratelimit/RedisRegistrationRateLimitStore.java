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
package com.cloudforge.iam.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.cloudforge.iam.identity.RegistrationRateLimitStore;
import com.cloudforge.iam.identity.RegistrationRateLimitUnavailableException;
import com.cloudforge.iam.identity.TokenBucket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
final class RedisRegistrationRateLimitStore implements RegistrationRateLimitStore {

	private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
			local now = tonumber(ARGV[1])
			local available = {}
			local refill = {}
			local capacity = {}
			local max_retry = 0

			for index, key in ipairs(KEYS) do
			  local argument = 2 + ((index - 1) * 2)
			  capacity[index] = tonumber(ARGV[argument])
			  refill[index] = tonumber(ARGV[argument + 1])
			  local state = redis.call('HMGET', key, 'token_units', 'updated_at')
			  local token_units = tonumber(state[1]) or capacity[index]
			  local updated_at = tonumber(state[2]) or now
			  local elapsed = math.max(0, now - updated_at)
			  available[index] = math.min(capacity[index], token_units + elapsed)
			  if available[index] < refill[index] then
			    max_retry = math.max(max_retry, refill[index] - available[index])
			  end
			end

			for index, key in ipairs(KEYS) do
			  local token_units = available[index]
			  if max_retry == 0 then
			    token_units = token_units - refill[index]
			  end
			  redis.call('HSET', key, 'token_units', token_units, 'updated_at', now)
			  redis.call('PEXPIRE', key, capacity[index])
			end

			return max_retry
			""", Long.class);

	private final StringRedisTemplate redis;

	private final String namespace;

	RedisRegistrationRateLimitStore(StringRedisTemplate redis,
			@Value("${cloudforge.iam.registration-rate-limit.namespace}") String namespace) {
		this.redis = redis;
		this.namespace = namespace;
	}

	@Override
	public Duration consume(List<TokenBucket> buckets, Instant now) {
		List<String> keys = buckets.stream().map(bucket -> redisKey(bucket.key())).toList();
		List<String> arguments = new ArrayList<>();
		arguments.add(Long.toString(now.toEpochMilli()));
		for (TokenBucket bucket : buckets) {
			long refillIntervalMillis = bucket.refillInterval().toMillis();
			arguments.add(Long.toString(Math.multiplyExact(bucket.capacity(), refillIntervalMillis)));
			arguments.add(Long.toString(refillIntervalMillis));
		}
		try {
			Long retryAfterMillis = this.redis.execute(CONSUME, keys, arguments.toArray());
			if (retryAfterMillis == null) {
				throw new RegistrationRateLimitUnavailableException();
			}
			return Duration.ofMillis(retryAfterMillis);
		}
		catch (RegistrationRateLimitUnavailableException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new RegistrationRateLimitUnavailableException();
		}
	}

	private String redisKey(String key) {
		return this.namespace + ":" + sha256(key);
	}

	private static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

}
