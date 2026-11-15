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

package com.phonepe.arnold.models;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ArnoldResponseTest {

    @Test
    void testBuilder() {
        ArnoldResponse<String> response = ArnoldResponse.<String>builder()
                .success(true)
                .response("Hello")
                .errorCode(0)
                .errorMessage("No Error")
                .build();

        Assertions.assertTrue(response.isSuccess());
        Assertions.assertEquals("Hello", response.getResponse());
        Assertions.assertEquals(0, response.getErrorCode());
        Assertions.assertEquals("No Error", response.getErrorMessage());
    }

    @Test
    void testOk() {
        String message = "Hello World!";
        ArnoldResponse<String> response = ArnoldResponse.ok(message);
        Assertions.assertTrue(response.isSuccess());
        Assertions.assertEquals(message, response.getResponse());
        Assertions.assertNull(response.getErrorCode());
        Assertions.assertNull(response.getErrorMessage());
    }

    @Test
    void testSetters() {
        ArnoldResponse<Integer> response = ArnoldResponse.<Integer>builder()
                .success(false)
                .response(10)
                .errorCode(500)
                .errorMessage("Internal Server Error")
                .build();

        Assertions.assertFalse(response.isSuccess());
        Assertions.assertEquals(10, response.getResponse());
        Assertions.assertEquals(500, response.getErrorCode());
        Assertions.assertEquals("Internal Server Error", response.getErrorMessage());
    }
}
