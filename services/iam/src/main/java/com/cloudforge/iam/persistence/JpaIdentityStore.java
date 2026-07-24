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
package com.cloudforge.iam.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.cloudforge.iam.identity.IdentityModule.UserProfile;
import com.cloudforge.iam.identity.IdentityStore;

import org.springframework.stereotype.Repository;

@Repository
class JpaIdentityStore implements IdentityStore {

	private final UserJpaRepository users;

	JpaIdentityStore(UserJpaRepository users) {
		this.users = users;
	}

	@Override
	public UserProfile create(String email, String passwordHash, Instant registeredAt) {
		return this.users.saveAndFlush(new UserEntity(email, passwordHash, registeredAt)).toProfile();
	}

	@Override
	public Optional<UserProfile> findById(UUID userId) {
		return this.users.findById(userId).map(UserEntity::toProfile);
	}

}
