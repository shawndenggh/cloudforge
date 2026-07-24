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
import java.util.HexFormat;
import java.util.List;

import com.cloudforge.iam.identity.LoginRateLimitStore;
import com.cloudforge.iam.identity.LoginRateLimitUnavailableException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
final class RedisLoginRateLimitStore implements LoginRateLimitStore {

	private static final long EMAIL_EXPIRY_MILLIS = Duration.ofHours(1).toMillis();

	private static final long SOURCE_WINDOW_MILLIS = Duration.ofMinutes(10).toMillis();

	private static final long SOURCE_COOLDOWN_MILLIS = Duration.ofMinutes(10).toMillis();

	private static final DefaultRedisScript<Long> CHECK = new DefaultRedisScript<>("""
			local now = tonumber(ARGV[1])
			local email_last_failure = tonumber(redis.call('HGET', KEYS[1], 'last_failure'))
			local email_cooldown_until = tonumber(redis.call('HGET', KEYS[1], 'cooldown_until')) or now
			if email_last_failure and now - email_last_failure >= tonumber(ARGV[2]) then
			  redis.call('DEL', KEYS[1])
			  email_cooldown_until = now
			end

			local source_window_started = tonumber(redis.call('HGET', KEYS[2], 'window_started'))
			local source_cooldown_until = tonumber(redis.call('HGET', KEYS[2], 'cooldown_until')) or now
			if source_window_started and source_cooldown_until <= now
			    and now - source_window_started >= tonumber(ARGV[3]) then
			  redis.call('DEL', KEYS[2])
			  source_cooldown_until = now
			end

			return math.max(0, email_cooldown_until - now, source_cooldown_until - now)
			""", Long.class);

	private static final DefaultRedisScript<Long> RECORD_FAILURE = new DefaultRedisScript<>("""
			local now = tonumber(ARGV[1])
			local email_expiry = tonumber(ARGV[2])
			local source_window = tonumber(ARGV[3])
			local source_cooldown = tonumber(ARGV[4])

			local email_state = redis.call('HMGET', KEYS[1], 'failure_count', 'last_failure')
			local email_failures = tonumber(email_state[1]) or 0
			local email_last_failure = tonumber(email_state[2])
			if email_last_failure and now - email_last_failure >= email_expiry then
			  email_failures = 0
			end
			email_failures = email_failures + 1
			local email_cooldown = 0
			if email_failures == 5 then
			  email_cooldown = 30000
			elseif email_failures == 6 then
			  email_cooldown = 60000
			elseif email_failures == 7 then
			  email_cooldown = 120000
			elseif email_failures == 8 then
			  email_cooldown = 240000
			elseif email_failures == 9 then
			  email_cooldown = 480000
			elseif email_failures >= 10 then
			  email_cooldown = 900000
			end
			redis.call('HSET', KEYS[1], 'failure_count', email_failures, 'last_failure', now,
			    'cooldown_until', now + email_cooldown)
			redis.call('PEXPIRE', KEYS[1], email_expiry)

			local source_state = redis.call('HMGET', KEYS[2], 'failure_count', 'window_started',
			    'cooldown_until')
			local source_failures = tonumber(source_state[1]) or 0
			local source_window_started = tonumber(source_state[2]) or now
			local source_cooldown_until = tonumber(source_state[3]) or now
			if source_cooldown_until <= now and now - source_window_started >= source_window then
			  source_failures = 0
			  source_window_started = now
			  source_cooldown_until = now
			end
			source_failures = source_failures + 1
			if source_failures > 50 then
			  source_cooldown_until = now + source_cooldown
			end
			redis.call('HSET', KEYS[2], 'failure_count', source_failures, 'window_started',
			    source_window_started, 'cooldown_until', source_cooldown_until)
			local source_ttl = math.max(1, source_window_started + source_window - now,
			    source_cooldown_until - now)
			redis.call('PEXPIRE', KEYS[2], source_ttl)

			return math.max(email_cooldown, source_cooldown_until - now)
			""", Long.class);

	private final StringRedisTemplate redis;

	private final String namespace;

	RedisLoginRateLimitStore(StringRedisTemplate redis,
			@Value("${cloudforge.iam.login-rate-limit.namespace}") String namespace) {
		this.redis = redis;
		this.namespace = namespace;
	}

	@Override
	public Duration check(String normalizedEmail, String clientIp, Instant now) {
		return execute(CHECK, keys(normalizedEmail, clientIp), now.toEpochMilli(), EMAIL_EXPIRY_MILLIS,
				SOURCE_WINDOW_MILLIS);
	}

	@Override
	public Duration recordFailure(String normalizedEmail, String clientIp, Instant now) {
		return execute(RECORD_FAILURE, keys(normalizedEmail, clientIp), now.toEpochMilli(), EMAIL_EXPIRY_MILLIS,
				SOURCE_WINDOW_MILLIS, SOURCE_COOLDOWN_MILLIS);
	}

	@Override
	public void clearEmail(String normalizedEmail) {
		try {
			Boolean deleted = this.redis.delete(redisKey("email:" + normalizedEmail));
			if (deleted == null) {
				throw new LoginRateLimitUnavailableException();
			}
		}
		catch (LoginRateLimitUnavailableException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new LoginRateLimitUnavailableException();
		}
	}

	private Duration execute(DefaultRedisScript<Long> script, List<String> keys, long... arguments) {
		String[] serializedArguments = new String[arguments.length];
		for (int index = 0; index < arguments.length; index++) {
			serializedArguments[index] = Long.toString(arguments[index]);
		}
		try {
			Long retryAfterMillis = this.redis.execute(script, keys, (Object[]) serializedArguments);
			if (retryAfterMillis == null) {
				throw new LoginRateLimitUnavailableException();
			}
			return Duration.ofMillis(retryAfterMillis);
		}
		catch (LoginRateLimitUnavailableException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new LoginRateLimitUnavailableException();
		}
	}

	private List<String> keys(String normalizedEmail, String clientIp) {
		return List.of(redisKey("email:" + normalizedEmail), redisKey("source:" + clientIp));
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
