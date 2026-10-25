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
                        .build())
                .build();
    }
}
