package com.phonepe.arnold.models.mergerequest;

public record ReviewerStatusResponse(
        long id,
        String name,
        String username,
        String avatarUrl,
        String status
) {
}
