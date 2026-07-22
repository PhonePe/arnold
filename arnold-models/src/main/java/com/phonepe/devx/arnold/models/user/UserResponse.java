package com.phonepe.devx.arnold.models.user;

import java.util.Set;

public record UserResponse(
        String id,
        String email,
        String username,
        String displayName,
        String avatarUrl,
        Long gitlabUserId,
        String gitlabUsername,
        Set<String> roles
) {
}
