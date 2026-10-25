package com.phonepe.arnold.core.exception;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;


@DisplayName("ErrorUtils Tests")
class ErrorUtilsTest {

    @Test
    @DisplayName("Test message() method with valid input")
    void testMessageWithValidInput() {
        ArnoldErrorCode errorCode = ArnoldErrorCode.INTERNAL_SERVER_ERROR;
        Map<String, Object> context = new HashMap<>();
        context.put("resource", "database");
        String expectedMessage = "Error [1000]: Internal Server Error";
        String actualMessage = ErrorUtils.message(errorCode, context);
        Assertions.assertEquals(expectedMessage, actualMessage);
    }

    @Test
    @DisplayName("Test message() method with empty context")
    void testMessageWithEmptyContext() {
        ArnoldErrorCode errorCode = ArnoldErrorCode.INTERNAL_SERVER_ERROR;
        Map<String, Object> context = new HashMap<>();
        String expectedMessage = "Error [1000]: Internal Server Error";
        String actualMessage = ErrorUtils.message(errorCode, context);
        Assertions.assertEquals(expectedMessage, actualMessage);
    }
}
