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

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.cloudforge.iam.identity.EmailAlreadyRegisteredException;
import com.cloudforge.iam.identity.IdentityModule.UserProfile;
import com.cloudforge.iam.identity.IdentityStore;
import com.cloudforge.iam.identity.IdentityStore.PasswordCredential;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class JpaIdentityStore implements IdentityStore {

	private final UserJpaRepository users;

	JpaIdentityStore(UserJpaRepository users) {
		this.users = users;
	}

	@Override
	public UserProfile create(String email, String passwordHash, Instant registeredAt) {
		try {
			return this.users.saveAndFlush(new UserEntity(email, passwordHash, registeredAt)).toProfile();
		}
		catch (DataIntegrityViolationException exception) {
			if (isEmailUniqueConstraintViolation(exception)) {
				throw new EmailAlreadyRegisteredException();
			}
			throw exception;
		}
	}

	@Override
	public Optional<UserProfile> findById(UUID userId) {
		return this.users.findById(userId).map(UserEntity::toProfile);
	}

	@Override
	public Optional<PasswordCredential> findCredentialByEmail(String email) {
		return this.users.findByEmail(email).map(UserEntity::toPasswordCredential);
	}

	private static boolean isEmailUniqueConstraintViolation(DataIntegrityViolationException exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
				String message = sqlException.getMessage();
				if (message != null && message.contains("users_email_key")) {
					return true;
				}
			}
			cause = cause.getCause();
		}
		return false;
	}

}
