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
package com.cloudforge.iam.config;

import java.util.Objects;

import com.password4j.Argon2Function;
import com.password4j.Password;

import org.springframework.security.crypto.password4j.Argon2Password4jPasswordEncoder;

final class CloudForgeArgon2PasswordEncoder extends Argon2Password4jPasswordEncoder {

	private static final int SALT_LENGTH_BYTES = 16;

	private final Argon2Function function;

	CloudForgeArgon2PasswordEncoder(Argon2Function function) {
		super(function);
		this.function = function;
	}

	@Override
	protected String encodeNonNullPassword(String rawPassword) {
		return Objects.requireNonNull(
				Password.hash(rawPassword).addRandomSalt(SALT_LENGTH_BYTES).with(this.function).getResult());
	}

	@Override
	protected boolean matchesNonNull(String rawPassword, String encodedPassword) {
		try {
			Argon2Function encodedFunction = Argon2Function.getInstanceFromHash(encodedPassword);
			return Password.check(rawPassword, encodedPassword).with(encodedFunction);
		}
		catch (RuntimeException exception) {
			return false;
		}
	}

	@Override
	protected boolean upgradeEncodingNonNull(String encodedPassword) {
		try {
			Argon2Function encodedFunction = Argon2Function.getInstanceFromHash(encodedPassword);
			if (encodedFunction.getVariant() != this.function.getVariant()
					|| encodedFunction.getVersion() != this.function.getVersion()) {
				return true;
			}
			boolean noParameterIsStronger = encodedFunction.getMemory() <= this.function.getMemory()
					&& encodedFunction.getIterations() <= this.function.getIterations()
					&& encodedFunction.getParallelism() <= this.function.getParallelism()
					&& encodedFunction.getOutputLength() <= this.function.getOutputLength();
			return noParameterIsStronger && !encodedFunction.equals(this.function);
		}
		catch (RuntimeException exception) {
			return false;
		}
	}

}
