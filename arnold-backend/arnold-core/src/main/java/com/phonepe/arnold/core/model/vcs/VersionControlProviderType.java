package com.phonepe.arnold.core.model.vcs;

import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;

import java.util.Locale;

public enum VersionControlProviderType {
    GITLAB,
    GITHUB;

    /**
     * Parses a provider name from an API parameter; blank values default to {@link #GITLAB} for compatibility
     * with clients that predate multi-provider support.
     */
    public static VersionControlProviderType parse(final String value) {
        if (value == null || value.isBlank()) {
            return GITLAB;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                    .message("Unsupported version control provider").build();
        }
    }
}
