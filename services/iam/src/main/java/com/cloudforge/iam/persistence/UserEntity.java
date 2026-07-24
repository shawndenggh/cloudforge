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
import java.util.Objects;
import java.util.UUID;

import com.cloudforge.iam.identity.IdentityModule.UserProfile;
import com.cloudforge.iam.identity.IdentityStore.PasswordCredential;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.UuidGenerator.Style;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "users")
class UserEntity {

	@Id
	@UuidGenerator(style = Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false)
	private @Nullable UUID id;

	@Column(name = "email", nullable = false, length = 254)
	private @Nullable String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private @Nullable String passwordHash;

	@Column(name = "created_at", nullable = false, updatable = false)
	private @Nullable Instant createdAt;

	protected UserEntity() {
	}

	UserEntity(String email, String passwordHash, Instant createdAt) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.createdAt = createdAt;
	}

	UserProfile toProfile() {
		return new UserProfile(Objects.requireNonNull(this.id), Objects.requireNonNull(this.email),
				Objects.requireNonNull(this.createdAt));
	}

	PasswordCredential toPasswordCredential() {
		return new PasswordCredential(Objects.requireNonNull(this.id), Objects.requireNonNull(this.passwordHash));
	}

}
