/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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
package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.model.sync.RepositorySyncResult;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;

/**
 * Imports every open change request from the repositories a deployment can access, independent of any user.
 */
public interface RepositorySyncProvider {
    VersionControlProviderType type();

    boolean available();

    RepositorySyncResult synchronize();

    static boolean rateLimited(final ArnoldError error) {
        final var context = error.getContext();
        return context != null && (Boolean.TRUE.equals(context.get("rateLimited"))
                || Integer.valueOf(429).equals(context.get("httpStatus")));
    }

    static boolean notFound(final ArnoldError error) {
        final var context = error.getContext();
        return error.getErrorCode() == com.phonepe.arnold.core.exception.ArnoldErrorCode.NOT_FOUND_ERROR
                || context != null && Integer.valueOf(404).equals(context.get("httpStatus"));
    }
}
