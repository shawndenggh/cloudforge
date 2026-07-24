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
package com.cloudforge.iam.session;

import java.util.UUID;

import com.cloudforge.iam.identity.IdentitySessionStore;

import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;

@Component
final class RedisIdentitySessionStore implements IdentitySessionStore {

	static final String USER_ID_ATTRIBUTE = "user_id";

	private final SessionRepository<?> sessions;

	RedisIdentitySessionStore(SessionRepository<?> sessions) {
		this.sessions = sessions;
	}

	@Override
	public String create(UUID userId) {
		return create(this.sessions, userId);
	}

	private static <S extends Session> String create(SessionRepository<S> sessions, UUID userId) {
		S session = sessions.createSession();
		session.setAttribute(USER_ID_ATTRIBUTE, userId.toString());
		sessions.save(session);
		return session.getId();
	}

}
