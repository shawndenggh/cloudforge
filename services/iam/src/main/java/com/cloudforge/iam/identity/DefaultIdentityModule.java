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

import java.text.Normalizer;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class DefaultIdentityModule implements IdentityModule {

	private final IdentityStore identityStore;

	private final PasswordHasher passwordHasher;

	private final IdentitySessionStore sessionStore;

	private final Clock clock;

	public DefaultIdentityModule(IdentityStore identityStore, PasswordHasher passwordHasher,
			IdentitySessionStore sessionStore, Clock clock) {
		this.identityStore = identityStore;
		this.passwordHasher = passwordHasher;
		this.sessionStore = sessionStore;
		this.clock = clock;
	}

	@Override
	public Registration register(RegisterCommand command) {
		String email = Normalizer.normalize(command.email().trim(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
		UserProfile user = this.identityStore.create(email, this.passwordHasher.hash(command.password()),
				this.clock.instant());
		return new Registration(user, this.sessionStore.create(user.id()));
	}

	@Override
	public Optional<UserProfile> findUser(UUID userId) {
		return this.identityStore.findById(userId);
	}

}
