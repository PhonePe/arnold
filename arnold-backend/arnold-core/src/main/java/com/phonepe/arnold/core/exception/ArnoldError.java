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

package com.phonepe.arnold.core.exception;

import com.google.common.base.Strings;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Map;

public class ArnoldError extends RuntimeException {
    @Getter
    private final ArnoldErrorCode errorCode;
    @Getter
    private final transient Map<String, Object> context;
    @Getter
    private final transient String parsedMessage;
    /**
     * Machine-readable fields exposed to API clients alongside the error code, e.g. which provider needs reconnecting.
     */
    @Getter
    private final transient Map<String, Object> details;

    private ArnoldError(
            final ArnoldErrorCode errorCode,
            final String message,
            final Throwable cause,
            final Map<String, Object> context,
            final String parsedMessage,
            final Map<String, Object> details
    ) {
        super(message, cause);
        this.details = details;
        this.errorCode = errorCode;
        this.context = context;
        this.parsedMessage = parsedMessage;
    }

    public static ArnoldErrorBuilder raise(final ArnoldErrorCode errorCode) {
        return new ArnoldErrorBuilder()
                .errorCode(errorCode);
    }

    public static ArnoldErrorBuilder propagate(final Throwable e) {
        return new ArnoldErrorBuilder()
                .cause(e);
    }

    @Setter
    @Accessors(fluent = true, chain = true)
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ArnoldErrorBuilder {
        private Throwable cause = null;
        @NonNull
        private ArnoldErrorCode errorCode = ArnoldErrorCode.INTERNAL_SERVER_ERROR;
        private Map<String, Object> context = Map.of();
        private String message = null;
        private Map<String, Object> details = Map.of();

        public ArnoldError build() {
            if (cause instanceof ArnoldError error) {
                return error;
            }

            final var parsedMessage = ErrorUtils.message(errorCode, context);
            final var errorMessage = createMessage(parsedMessage);

            return new ArnoldError(errorCode, errorMessage, cause, context, parsedMessage, details);
        }

        private String createMessage(final String fallback) {
            if (cause == null) {
                return Strings.isNullOrEmpty(message) ? fallback : message;
            }
            final var errorMessage = "Error: " + cause.getMessage();
            if (Strings.isNullOrEmpty(message)) {
                return errorMessage;
            }
            return message + " " + errorMessage;
        }
    }
}
