package com.phonepe.devx.arnold.core.auth;

import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.olympus.im.client.security.ServiceUserPrincipal;
import com.phonepe.olympus.im.models.user.UserDetails;

public final class AuthenticatedUser {
    private AuthenticatedUser() {
    }

    public static UserDetails details(final ServiceUserPrincipal principal) {
        if (principal == null || principal.getUserAuthDetails() == null
                || principal.getUserAuthDetails().getUserDetails() == null) {
            throw ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR).build();
        }
        return principal.getUserAuthDetails().getUserDetails();
    }

    public static String id(final ServiceUserPrincipal principal) {
        final var userId = details(principal).getUserId();
        if (userId.isBlank()) {
            throw ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR).build();
        }
        return userId;
    }
}
