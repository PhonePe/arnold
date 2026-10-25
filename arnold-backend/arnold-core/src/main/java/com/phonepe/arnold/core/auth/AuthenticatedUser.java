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

package com.phonepe.arnold.core.auth;

import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;

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
