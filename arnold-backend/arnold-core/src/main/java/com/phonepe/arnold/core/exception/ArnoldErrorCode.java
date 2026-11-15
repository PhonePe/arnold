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

package com.phonepe.arnold.core.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import javax.ws.rs.core.Response;

@Getter
@AllArgsConstructor
public enum ArnoldErrorCode {

    SUCCESS(0, "Success", Response.Status.OK),

    /* 1xxx for internal errors */
    INTERNAL_SERVER_ERROR(1000, "Internal Server Error", Response.Status.INTERNAL_SERVER_ERROR),
    WEB_APP_ERROR(1001, "Internal Web App Error", Response.Status.INTERNAL_SERVER_ERROR),
    JSON_SER_ERROR(1002, "Internal serialization Error", Response.Status.INTERNAL_SERVER_ERROR),
    JSON_DESER_ERROR(1003, "Internal deserialization Error", Response.Status.INTERNAL_SERVER_ERROR),
    DB_ERROR(1004, "Internal Database error", Response.Status.INTERNAL_SERVER_ERROR),

    /* 2xxx for auth and permissions */
    AUTH_ERROR(2000, "Authorization Error", Response.Status.UNAUTHORIZED),
    FORBIDDEN_ERROR(2001, "Forbidden Error", Response.Status.FORBIDDEN),
    NOT_FOUND_ERROR(2002, "Not Found", Response.Status.NOT_FOUND),
    OPERATION_NOT_ALLOWED(2003, "Operation is not allowed", Response.Status.METHOD_NOT_ALLOWED),

    /* 3xxx for validation */
    WRONG_INPUT_ERROR(3000, "Invalid Input", Response.Status.BAD_REQUEST),
    CONFLICT_ERROR(3001, "Concurrent update conflict", Response.Status.CONFLICT),
    INVALID_USER_FOR_CREATION(3002, "Concurrent update conflict", Response.Status.BAD_REQUEST),

    /* 4xxx for client errors */
    CLIENT_FAILURE(4000, "Failed to call client", Response.Status.INTERNAL_SERVER_ERROR),
    GITLAB_PIPELINE_FAILURE(4001, "Failed to call client", Response.Status.INTERNAL_SERVER_ERROR),

    /* 5xxx for start-up errors */
    INIT_ERROR(5000, "Unable to initialize", Response.Status.INTERNAL_SERVER_ERROR),
    ;

    private final int code;
    private final String message;
    private final Response.Status httpStatusCode;
}
