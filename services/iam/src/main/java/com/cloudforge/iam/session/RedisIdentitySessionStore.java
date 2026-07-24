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
import com.cloudforge.iam.identity.IdentitySessionRevocationUnavailableException;
import com.cloudforge.iam.identity.IdentitySessionUnavailableException;
import jakarta.servlet.http.HttpSession;

import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
final class RedisIdentitySessionStore implements IdentitySessionStore {

	static final String USER_ID_ATTRIBUTE = "user_id";

	private final SessionRepository<?> sessions;

	RedisIdentitySessionStore(SessionRepository<?> sessions) {
		this.sessions = sessions;
	}

	@Override
	public String create(UUID userId) {
		try {
			return create(this.sessions, userId);
		}
		catch (RuntimeException exception) {
			throw new IdentitySessionUnavailableException();
		}
	}

	@Override
	public void revoke(String sessionId) {
		try {
			if (!invalidateCurrentRequestSession(sessionId)) {
				this.sessions.deleteById(sessionId);
			}
		}
		catch (RuntimeException exception) {
			throw new IdentitySessionRevocationUnavailableException();
		}
	}

	private static boolean invalidateCurrentRequestSession(String sessionId) {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return false;
		}
		HttpSession session = attributes.getRequest().getSession(false);
		if (session == null || !session.getId().equals(sessionId)) {
			return false;
		}
		session.invalidate();
		return true;
	}

	private static <S extends Session> String create(SessionRepository<S> sessions, UUID userId) {
		S session = sessions.createSession();
		session.setAttribute(USER_ID_ATTRIBUTE, userId.toString());
		sessions.save(session);
		return session.getId();
	}

}
