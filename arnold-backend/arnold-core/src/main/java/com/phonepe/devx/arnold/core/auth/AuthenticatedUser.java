package com.phonepe.devx.arnold.core.auth;

import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;

public final class AuthenticatedUser {
    private AuthenticatedUser() {
    }

    public static ArnoldPrincipal details(final ArnoldPrincipal principal) {
        if (principal == null) {
            throw ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR).build();
        }
        return principal;
    }

    public static String id(final ArnoldPrincipal principal) {
        final var userId = details(principal).getId();
        if (userId.isBlank()) {
            throw ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR).build();
        }
        return userId;
    }
}
