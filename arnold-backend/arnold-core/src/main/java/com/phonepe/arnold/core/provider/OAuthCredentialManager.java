/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.provider;

import com.google.common.util.concurrent.Striped;
import com.livetheoogway.crudstore.core.ConcurrentStore;
import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.vcs.VersionControlCredential;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;

/**
 * Hands out usable user OAuth credentials for one provider and keeps refresh-token rotation safe.
 *
 * <p>GitHub and GitLab invalidate a refresh token once it is used, so two refreshes of the same credential must
 * never race: refreshes are serialized per credential in-process, and the stored record is written with a version
 * check so that a refresh completed by another node is adopted instead of being repeated. A credential the provider
 * no longer accepts is kept but marked {@value #RECONNECT_REQUIRED}, and callers receive an
 * {@link ArnoldErrorCode#AUTH_ERROR} carrying {@link #reconnectDetails}.</p>
 */
@Slf4j
public class OAuthCredentialManager {
    public static final String CONNECTED = "connected";
    public static final String RECONNECT_REQUIRED = "reconnect_required";
    public static final String RECONNECT_REASON = "SCM_RECONNECT_REQUIRED";

    private static final int MAX_WRITE_ATTEMPTS = 3;

    public record Token(String accessToken, String refreshToken, Long expiresAt, Set<String> scopes) {
    }

    private final ConcurrentStore<VersionControlCredential, Long> store;
    private final VersionControlProviderType provider;
    private final long refreshBufferMs;
    private final Function<String, Token> refresh;
    private final Clock clock;
    private final Striped<Lock> locks = Striped.lazyWeakLock(64);

    public OAuthCredentialManager(final ConcurrentStore<VersionControlCredential, Long> store,
                                  final VersionControlProviderType provider, final long refreshBufferMs,
                                  final Function<String, Token> refresh, final Clock clock) {
        this.store = store;
        this.provider = provider;
        this.refreshBufferMs = refreshBufferMs;
        this.refresh = refresh;
        this.clock = clock;
    }

    /**
     * @return a credential whose access token is not about to expire, refreshing it when needed
     */
    public VersionControlCredential valid(final String userId) {
        final var id = VersionControlCredential.key(provider, userId);
        final var current = read(id).value();
        if (fresh(current)) {
            return current;
        }
        final var lock = locks.get(id);
        lock.lock();
        try {
            return refreshLocked(id);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Handles an HTTP 401 the provider returned for a call made with {@code used}.
     *
     * @return a newer credential to retry with when the token was rotated concurrently
     * @throws ArnoldError the reconnect signal once the credential is marked {@value #RECONNECT_REQUIRED}
     */
    public VersionControlCredential unauthorized(final String userId, final VersionControlCredential used) {
        final var id = VersionControlCredential.key(provider, userId);
        final var lock = locks.get(id);
        lock.lock();
        try {
            final var latest = store.getWithVersion(id).orElseThrow(this::notConnected);
            final var stored = latest.value();
            if (!RECONNECT_REQUIRED.equals(stored.status())
                    && !Objects.equals(stored.accessToken(), used.accessToken()) && fresh(stored)) {
                return stored;
            }
            markReconnectRequired(latest);
            throw reconnectRequired("rejected the access token");
        } finally {
            lock.unlock();
        }
    }

    public static boolean unauthorized(final ArnoldError error) {
        return error.getContext() != null && Integer.valueOf(401).equals(error.getContext().get("httpStatus"));
    }

    public static Map<String, Object> reconnectDetails(final VersionControlProviderType provider) {
        return Map.of("reason", RECONNECT_REASON, "provider", provider.name(), "reconnect", true);
    }

    private VersionControlCredential refreshLocked(final String id) {
        var versioned = read(id);
        for (var attempt = 0; attempt < MAX_WRITE_ATTEMPTS; attempt++) {
            final var credential = versioned.value();
            if (fresh(credential)) {
                return credential;
            }
            if (credential.refreshToken() == null || credential.refreshToken().isBlank()) {
                markReconnectRequired(versioned);
                throw reconnectRequired("credential has expired");
            }
            final Token token;
            try {
                token = refresh.apply(credential.refreshToken());
            } catch (ArnoldError error) {
                if (!revoked(error)) {
                    throw error;
                }
                final var latest = read(id);
                if (!Objects.equals(latest.version(), versioned.version()) && fresh(latest.value())) {
                    // Another node rotated the refresh token first; its result is the one to use.
                    return latest.value();
                }
                markReconnectRequired(latest);
                throw reconnectRequired("refresh token was rejected");
            }
            final var refreshed = new VersionControlCredential(credential.id(), credential.userId(),
                    credential.provider(), credential.credentialType(), token.accessToken(),
                    token.refreshToken() == null ? credential.refreshToken() : token.refreshToken(),
                    credential.providerUserId(), credential.providerUsername(),
                    token.scopes() == null || token.scopes().isEmpty() ? credential.scopes() : token.scopes(),
                    token.expiresAt(), CONNECTED, credential.createdAt(), clock.millis());
            try {
                store.checkAndUpdate(refreshed, versioned.version());
                return refreshed;
            } catch (ConcurrentUpdateException conflict) {
                final var latest = store.getWithVersion(id).orElseThrow(this::notConnected);
                if (fresh(latest.value())) {
                    return latest.value();
                }
                // The concurrent writer could not refresh (e.g. it lost the rotation race to this node and marked
                // the credential); the token minted here is the valid one, so persist it on top.
                try {
                    store.checkAndUpdate(refreshed, latest.version());
                    return refreshed;
                } catch (ConcurrentUpdateException again) {
                    versioned = read(id);
                }
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR)
                .message(display() + " credential is being updated concurrently; retry").build();
    }

    private Versioned<VersionControlCredential, Long> read(final String id) {
        final var versioned = store.getWithVersion(id).orElseThrow(this::notConnected);
        if (RECONNECT_REQUIRED.equals(versioned.value().status())) {
            throw reconnectRequired("credential needs to be reconnected");
        }
        return versioned;
    }

    private boolean fresh(final VersionControlCredential credential) {
        return !RECONNECT_REQUIRED.equals(credential.status()) && (credential.expiresAt() == null
                || clock.millis() < credential.expiresAt() - refreshBufferMs);
    }

    private void markReconnectRequired(final Versioned<VersionControlCredential, Long> versioned) {
        final var credential = versioned.value();
        if (RECONNECT_REQUIRED.equals(credential.status())) {
            return;
        }
        try {
            store.checkAndUpdate(new VersionControlCredential(credential.id(), credential.userId(),
                    credential.provider(), credential.credentialType(), credential.accessToken(),
                    credential.refreshToken(), credential.providerUserId(), credential.providerUsername(),
                    credential.scopes(), credential.expiresAt(), RECONNECT_REQUIRED, credential.createdAt(),
                    clock.millis()), versioned.version());
            log.warn("{} credential {} needs to be reconnected", display(), credential.id());
        } catch (ConcurrentUpdateException conflict) {
            log.info("{} credential {} changed while marking it for reconnect", display(), credential.id());
        }
    }

    private static boolean revoked(final ArnoldError error) {
        final var context = error.getContext() == null ? Map.<String, Object>of() : error.getContext();
        final var oauthError = context.get("oauthError");
        if ("invalid_grant".equals(oauthError) || "bad_refresh_token".equals(oauthError)) {
            return true;
        }
        if (Integer.valueOf(401).equals(context.get("httpStatus"))) {
            return true;
        }
        final var message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("invalid_grant") || message.contains("bad_refresh_token");
    }

    private ArnoldError notConnected() {
        return ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR)
                .message(display() + " account is not connected")
                .details(Map.of("reason", "SCM_NOT_CONNECTED", "provider", provider.name(), "reconnect", true))
                .build();
    }

    private ArnoldError reconnectRequired(final String why) {
        return ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR)
                .message(display() + " " + why + "; reconnect the account")
                .details(reconnectDetails(provider))
                .build();
    }

    private String display() {
        return provider == VersionControlProviderType.GITHUB ? "GitHub" : "GitLab";
    }
}
