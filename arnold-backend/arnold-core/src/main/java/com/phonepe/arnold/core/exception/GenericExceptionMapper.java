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

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Slf4j
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(final Exception e) {
        if (e instanceof WebApplicationException webApplicationException) {
            Response response = webApplicationException.getResponse();
            return Response
                    .status(response.getStatus())
                    .entity(ArnoldResponse.builder()
                                    .success(false)
                                    .errorCode(response.getStatus())
                                    .errorMessage(webApplicationException.getMessage())
                                    .build())
                    .build();
        }
        log.error("Error handling request", e);
        return Response.serverError()
                .entity(ArnoldResponse.builder()
                        .success(false)
                        .errorCode(ArnoldErrorCode.INTERNAL_SERVER_ERROR.getCode())
                        .errorMessage(ArnoldErrorCode.INTERNAL_SERVER_ERROR.getMessage())
                        .build())
                .build();
    }
}
