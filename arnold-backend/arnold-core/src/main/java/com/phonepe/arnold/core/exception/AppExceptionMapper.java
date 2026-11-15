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

import com.phonepe.arnold.models.ArnoldResponse;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Slf4j
@Provider
public class AppExceptionMapper implements ExceptionMapper<ArnoldError> {

    @Override
    public Response toResponse(final ArnoldError error) {
        log.error("Error handling request: ", error);
        return Response.status(error.getErrorCode().getHttpStatusCode())
                .entity(ArnoldResponse.builder()
                        .success(error.getErrorCode() == ArnoldErrorCode.SUCCESS)
                        .errorCode(error.getErrorCode().getCode())
                        .errorMessage(error.getParsedMessage())
                        .details(error.getDetails() == null || error.getDetails().isEmpty() ? null
                                : error.getDetails())
                        .build())
                .build();
    }
}
