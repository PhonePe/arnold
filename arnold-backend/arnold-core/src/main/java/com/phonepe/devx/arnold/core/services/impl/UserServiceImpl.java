package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.user.ArnoldUser;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.services.UserService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeUserStore;
import com.phonepe.devx.arnold.core.utils.NameUtils;
import com.phonepe.devx.arnold.models.user.UserResponse;
import com.phonepe.olympus.im.models.user.HumanUserDetails;
import com.phonepe.olympus.im.models.user.SystemUserDetails;
import com.phonepe.olympus.im.models.user.UserDetails;
import com.phonepe.olympus.im.models.user.UserDetailsVisitor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Set;

@Singleton
public class UserServiceImpl implements UserService {
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
    public void deleteUser(String userId) {
        userStore.delete(userId);
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
        final var gitlabIdentity = user.providerIdentities().get(VersionControlProviderType.GITLAB);
        return new UserResponse(user.id(), user.email(), user.username(), user.displayName(),
                gitlabIdentity == null ? null : gitlabIdentity.avatarUrl(),
                gitlabIdentity == null ? null : Long.valueOf(gitlabIdentity.providerUserId()),
                gitlabIdentity == null ? null : gitlabIdentity.username(), user.roles());
    }
}
