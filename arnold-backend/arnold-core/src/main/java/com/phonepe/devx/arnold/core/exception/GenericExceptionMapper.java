package com.phonepe.devx.arnold.core.exception;

import com.phonepe.devx.arnold.models.ArnoldResponse;
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
