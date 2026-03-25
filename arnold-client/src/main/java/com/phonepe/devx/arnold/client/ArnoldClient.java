package com.phonepe.devx.arnold.client;

import com.phonepe.devx.arnold.models.ArnoldResponse;

import javax.annotation.Nullable;

public interface ArnoldClient {
    // todo replace this with a proper service impl, or delete this entire module
    default ArnoldResponse<String> ping(final String prefix) {
        return ping(prefix, null);
    }

    ArnoldResponse<String> ping(final String prefix, final @Nullable String message);
}
