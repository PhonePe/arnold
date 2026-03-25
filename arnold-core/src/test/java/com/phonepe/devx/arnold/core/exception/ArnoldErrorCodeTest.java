package com.phonepe.devx.arnold.core.exception;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ArnoldErrorCodeTest {

    @Test
    void testSuccess() {
        ArnoldErrorCode code = ArnoldErrorCode.SUCCESS;
        Assertions.assertEquals(0, code.getCode());
        Assertions.assertEquals("Success", code.getMessage());
        Assertions.assertEquals(200, code.getHttpStatusCode().getStatusCode());
    }

    @Test
    void testInternalServerError() {
        ArnoldErrorCode code = ArnoldErrorCode.INTERNAL_SERVER_ERROR;
        Assertions.assertEquals(1000, code.getCode());
        Assertions.assertEquals("Internal Server Error", code.getMessage());
        Assertions.assertEquals(500, code.getHttpStatusCode().getStatusCode());
    }

    @Test
    void testWebAppError() {
        ArnoldErrorCode code = ArnoldErrorCode.WEB_APP_ERROR;
        Assertions.assertEquals(1001, code.getCode());
        Assertions.assertEquals("Internal Web App Error", code.getMessage());
        Assertions.assertEquals(500, code.getHttpStatusCode().getStatusCode());
    }

    @Test
    void testJsonSerError() {
        ArnoldErrorCode code = ArnoldErrorCode.JSON_SER_ERROR;
        Assertions.assertEquals(1002, code.getCode());
        Assertions.assertEquals("Internal serialization Error", code.getMessage());
        Assertions.assertEquals(500, code.getHttpStatusCode().getStatusCode());
    }

    @Test
    void testJsonDeserError() {
        ArnoldErrorCode code = ArnoldErrorCode.JSON_DESER_ERROR;
        Assertions.assertEquals(1003, code.getCode());
        Assertions.assertEquals("Internal deserialization Error", code.getMessage());
        Assertions.assertEquals(500, code.getHttpStatusCode().getStatusCode());
    }

    @Test
    void testDbError() {
        ArnoldErrorCode code = ArnoldErrorCode.DB_ERROR;
        Assertions.assertEquals(1004, code.getCode());
        Assertions.assertEquals("Internal Database error", code.getMessage());
        Assertions.assertEquals(500, code.getHttpStatusCode().getStatusCode());
    }

    @Test
    void testAuthError() {
        ArnoldErrorCode code = ArnoldErrorCode.AUTH_ERROR;
        Assertions.assertEquals(2000, code.getCode());
        Assertions.assertEquals("Authorization Error", code.getMessage());
        Assertions.assertEquals(401, code.getHttpStatusCode().getStatusCode());
    }

    @Test
    void testForbiddenError() {
        ArnoldErrorCode code = ArnoldErrorCode.FORBIDDEN_ERROR;
        Assertions.assertEquals(2001, code.getCode());
        Assertions.assertEquals("Forbidden Error", code.getMessage());
        Assertions.assertEquals(403, code.getHttpStatusCode().getStatusCode());
    }

    @Test
    void testOperationNotAllowed() {
        ArnoldErrorCode code = ArnoldErrorCode.OPERATION_NOT_ALLOWED;
        Assertions.assertEquals(2003, code.getCode());
        Assertions.assertEquals("Operation is not allowed", code.getMessage());
        Assertions.assertEquals(405, code.getHttpStatusCode().getStatusCode());
    }

    @Test
    void testWrongInputError() {
        ArnoldErrorCode code = ArnoldErrorCode.WRONG_INPUT_ERROR;
        Assertions.assertEquals(3000, code.getCode());
        Assertions.assertEquals("Invalid Input", code.getMessage());
        Assertions.assertEquals(400, code.getHttpStatusCode().getStatusCode());
    }

    @Test
    void testInitError() {
        ArnoldErrorCode code = ArnoldErrorCode.INIT_ERROR;
        Assertions.assertEquals(5000, code.getCode());
        Assertions.assertEquals("Unable to initialize", code.getMessage());
        Assertions.assertEquals(500, code.getHttpStatusCode().getStatusCode());
    }
}
