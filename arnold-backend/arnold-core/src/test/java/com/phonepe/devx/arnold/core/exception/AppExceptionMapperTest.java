package com.phonepe.devx.arnold.core.exception;

import com.phonepe.devx.arnold.models.ArnoldResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.core.Response;

@DisplayName("AppExceptionMapper Tests")
@ExtendWith(MockitoExtension.class)
class AppExceptionMapperTest {

    private ArnoldError error = ArnoldError
            .raise(ArnoldErrorCode.INTERNAL_SERVER_ERROR)
            .message("An internal error occurred")
            .build();

    @Test
    @DisplayName("Test if toResponse returns the expected response")
    void testToResponseReturnsExpectedResponse() {
        // Create an instance of AppExceptionMapper
        AppExceptionMapper appExceptionMapper = new AppExceptionMapper();

        // Call the toResponse method and check the returned response
        Response response = appExceptionMapper.toResponse(error);

        Assertions.assertEquals(response.getStatus(), ArnoldErrorCode.INTERNAL_SERVER_ERROR.getHttpStatusCode().getStatusCode());

        ArnoldResponse ArnoldResponse = (ArnoldResponse) response.getEntity();
        Assertions.assertEquals(ArnoldErrorCode.INTERNAL_SERVER_ERROR.getCode(), ArnoldResponse.getErrorCode());
        Assertions.assertEquals("Error [1000]: Internal Server Error", ArnoldResponse.getErrorMessage());
        Assertions.assertFalse(ArnoldResponse.isSuccess());
    }
}
