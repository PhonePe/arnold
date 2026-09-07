package com.phonepe.devx.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.user.ArnoldUser;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.model.vcs.ProviderIdentity;
import com.phonepe.devx.arnold.core.services.UserService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeUserStore;
import com.phonepe.devx.arnold.core.utils.NameUtils;
import com.phonepe.devx.arnold.models.user.ProviderIdentityResponse;
import com.phonepe.devx.arnold.models.user.UserResponse;
import com.phonepe.olympus.im.models.user.HumanUserDetails;
import com.phonepe.olympus.im.models.user.SystemUserDetails;
import com.phonepe.olympus.im.models.user.UserDetails;
import com.phonepe.olympus.im.models.user.UserDetailsVisitor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Comparator;
import java.util.HashMap;
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
    public UserResponse getOrCreateCurrentUser(UserDetails userDetails) {
        final var user = userStore.get(userDetails.getUserId())
                .orElseGet(() -> persistUser(createUser(userDetails)));
        return toResponse(user);
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
            final var identities = new HashMap<>(current.value().providerIdentities());
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

    private ArnoldUser createUser(UserDetails userDetails) {
        final var now = System.currentTimeMillis();
        return userDetails.accept(new UserDetailsVisitor<>() {
            @Override
            public ArnoldUser visit(HumanUserDetails humanUserDetails) {
                final var username = NameUtils.sanitizedName(humanUserDetails);
                return new ArnoldUser(userDetails.getUserId(), humanUserDetails.getEmail(),
                        username,
                        username.split(" ")[0], Map.of(), Set.of(), now, now);
            }

            @Override
            public ArnoldUser visit(SystemUserDetails systemUserDetails) {
                throw ArnoldError.raise(ArnoldErrorCode.INVALID_USER_FOR_CREATION).build();
            }
        });
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
