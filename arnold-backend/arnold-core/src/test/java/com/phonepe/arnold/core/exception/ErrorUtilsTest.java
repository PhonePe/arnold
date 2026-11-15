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
