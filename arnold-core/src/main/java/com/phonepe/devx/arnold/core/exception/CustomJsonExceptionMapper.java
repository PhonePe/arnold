package com.phonepe.devx.arnold.core.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Slf4j
@Provider
public class CustomJsonExceptionMapper implements ExceptionMapper<JsonProcessingException> {

    @Override
    public Response toResponse(final JsonProcessingException e) {
        log.error("error: {}", ExceptionUtils.getRootCause(e), e);
        return Response.serverError()
                .entity(ArnoldResponse.builder()
                        .success(false)
                        .errorCode(ArnoldErrorCode.JSON_SER_ERROR.getCode())
                        .errorMessage(ExceptionUtils.getRootCauseMessage(e))
                        .build())
                .build();
    }
}
