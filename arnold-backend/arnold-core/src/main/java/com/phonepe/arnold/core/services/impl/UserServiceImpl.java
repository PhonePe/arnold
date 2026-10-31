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

package com.phonepe.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.google.common.base.Strings;
import com.phonepe.arnold.core.auth.ArnoldPrincipal;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.user.ArnoldUser;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.model.vcs.ProviderIdentity;
import com.phonepe.arnold.core.services.UserService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeUserStore;
import com.phonepe.arnold.core.utils.NameUtils;
import com.phonepe.arnold.models.user.ProviderIdentityResponse;
import com.phonepe.arnold.models.user.UserResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class UserServiceImpl implements UserService {
    private static final int MAX_UPDATE_ATTEMPTS = 8;
    private final AerospikeUserStore userStore;

    @Inject
    public UserServiceImpl(final AerospikeUserStore userStore) {
        this.userStore = userStore;
    }

    @Override
    public UserResponse getOrCreateCurrentUser(final ArnoldPrincipal principal) {
        final var existing = userStore.get(principal.getId());
        if (existing.isEmpty()) {
            return toResponse(persistUser(createUser(principal)));
        }
        final var created = createUser(principal);
        // Self-heal display names recorded before full names were stored.
        if (!existing.get().email().equals(created.email())
                || existing.get().displayName() == null || existing.get().displayName().isBlank()
                || existing.get().displayName().length() < created.displayName().length()) {
            final var healed = new ArnoldUser(existing.get().id(), created.email(), created.username(),
                    created.displayName(), existing.get().providerIdentities(), existing.get().roles(),
                    existing.get().createdAt(), System.currentTimeMillis());
            try {
                userStore.checkAndUpdate(healed, userStore.getWithVersion(existing.get().id()).orElseThrow().version());
                return toResponse(healed);
            } catch (ConcurrentUpdateException conflict) {
                return toResponse(userStore.get(existing.get().id()).orElse(existing.get()));
            }
        }
        return toResponse(existing.get());
    }

    @Override
    public List<UserResponse> list() {
        // ponytail: the user catalog is small; add indexed search when full scans become measurable.
        return userStore.list().stream().map(this::toResponse)
                .sorted(Comparator.comparing(user -> user.displayName() == null ? "" : user.displayName(),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public void deleteUser(String userId) {
        userStore.delete(userId);
    }

    @Override
    public UserResponse linkProviderIdentity(final String userId,
                                             final VersionControlProviderType provider,
                                             final ProviderIdentity identity) {
        for (var attempt = 0; attempt < MAX_UPDATE_ATTEMPTS; attempt++) {
            final var current = userStore.getWithVersion(userId)
                    .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
            final var identities = new EnumMap<VersionControlProviderType, ProviderIdentity>(VersionControlProviderType.class);
            identities.putAll(current.value().providerIdentities());
            identities.put(provider, identity);
            final var user = new ArnoldUser(current.value().id(), current.value().email(), current.value().username(),
                    current.value().displayName(), identities, current.value().roles(), current.value().createdAt(),
                    System.currentTimeMillis());
            try {
                userStore.checkAndUpdate(user, current.version());
                return toResponse(user);
            } catch (ConcurrentUpdateException conflict) {
                // Re-read the user and retry.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private ArnoldUser createUser(final ArnoldPrincipal principal) {
        if (principal.getType() != ArnoldPrincipal.Type.USER) {
            throw ArnoldError.raise(ArnoldErrorCode.INVALID_USER_FOR_CREATION).build();
        }
        final var now = System.currentTimeMillis();
        final var username = NameUtils.sanitizedName(principal);
        final var fullName = Strings.isNullOrEmpty(principal.getDisplayName())
                ? username : principal.getDisplayName().trim();
        return new ArnoldUser(principal.getId(), Strings.nullToEmpty(principal.getEmail()), username, fullName,
                Map.of(), Set.of(), now, now);
    }

    private ArnoldUser persistUser(final ArnoldUser user) {
        try {
            userStore.create(user);
            return user;
        } catch (RuntimeException createFailure) {
            return userStore.get(user.id()).orElseThrow(() -> createFailure);
        }
    }

    private UserResponse toResponse(final ArnoldUser user) {
        final var identities = user.providerIdentities().entrySet().stream().collect(Collectors.toUnmodifiableMap(
                entry -> entry.getKey().name(),
                entry -> new ProviderIdentityResponse(entry.getValue().providerUserId(),
                        entry.getValue().username(), entry.getValue().avatarUrl())));
        final var avatarUrl = identities.values().stream().map(ProviderIdentityResponse::avatarUrl)
                .filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
        return new UserResponse(user.id(), user.email(), user.username(), user.displayName(),
                avatarUrl, identities, user.roles());
    }
}
