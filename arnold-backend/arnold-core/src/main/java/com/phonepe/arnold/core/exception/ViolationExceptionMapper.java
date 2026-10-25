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

import com.phonepe.arnold.models.ArnoldResponse;
import io.dropwizard.jersey.validation.ConstraintMessage;
import io.dropwizard.jersey.validation.JerseyViolationException;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.stream.Collectors;

@Slf4j
@Provider
public class ViolationExceptionMapper implements ExceptionMapper<JerseyViolationException> {

    @Override
    public Response toResponse(final JerseyViolationException exception) {
        log.error("Object validation exception: ", exception);
        final var violations = exception.getConstraintViolations();
        final var invocable = exception.getInvocable();
        final var errorMessage = violations.stream()
                .map(violation -> ConstraintMessage.getMessage(violation, invocable))
                .collect(Collectors.joining(", "));
        int status = ConstraintMessage.determineStatus(violations, invocable);

        return Response.status(status)
                .entity(ArnoldResponse.builder()
                        .success(false)
                        .errorCode(ArnoldErrorCode.WRONG_INPUT_ERROR.getCode())
                        .errorMessage(errorMessage)
                        .build())
                .build();
    }
}
