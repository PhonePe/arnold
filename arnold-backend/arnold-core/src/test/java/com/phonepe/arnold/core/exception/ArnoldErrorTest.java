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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class ArnoldErrorTest {

    @Test
    void testArnoldErrorBuilder_build() {
        ArnoldError error = ArnoldError.raise(ArnoldErrorCode.INTERNAL_SERVER_ERROR)
                .message("Something went wrong")
                .cause(new Exception("Error occurred"))
                .context(Collections.singletonMap("key", "value"))
                .build();

        assertEquals(ArnoldErrorCode.INTERNAL_SERVER_ERROR, error.getErrorCode());
        assertEquals("Something went wrong Error: Error occurred", error.getMessage());
        assertEquals(Collections.singletonMap("key", "value"), error.getContext());
    }

    @Test
    void testArnoldErrorBuilder_build_withArnoldErrorCause() {
        ArnoldError serviceError = ArnoldError.raise(ArnoldErrorCode.INTERNAL_SERVER_ERROR).build();
        ArnoldError error = ArnoldError.propagate(serviceError).build();

        assertEquals(ArnoldErrorCode.INTERNAL_SERVER_ERROR, error.getErrorCode());
    }

    @Test
    void testArnoldErrorBuilder_build_withCustomMessage() {
        ArnoldError error = ArnoldError.raise(ArnoldErrorCode.INTERNAL_SERVER_ERROR)
                .message("Custom message")
                .build();

        assertEquals("Custom message", error.getMessage());
    }

    @Test
    void testArnoldErrorBuilder_build_withoutCustomMessage() {
        ArnoldError error = ArnoldError.raise(ArnoldErrorCode.INTERNAL_SERVER_ERROR).build();

        assertNotNull(error.getMessage());
    }

    @Test
    void testArnoldErrorBuilder_build_withoutCause() {
        ArnoldError error = ArnoldError.raise(ArnoldErrorCode.INTERNAL_SERVER_ERROR)
                .message("Something went wrong")
                .build();

        assertEquals("Something went wrong", error.getMessage());
    }

    @Test
    void testArnoldErrorBuilder_build_withCause() {
        ArnoldError error = ArnoldError.raise(ArnoldErrorCode.INTERNAL_SERVER_ERROR)
                .message("Something went wrong")
                .cause(new Exception("Error occurred"))
                .build();

        assertEquals("Something went wrong Error: Error occurred", error.getMessage());
    }

    @Test
    void testArnoldErrorBuilder_build_withParsedMessage() {
        Map<String, Object> context = new HashMap<>();
        context.put("key", "value");
        ArnoldError error = ArnoldError.raise(ArnoldErrorCode.INTERNAL_SERVER_ERROR)
                .context(context)
                .build();

        assertNotNull(error.getParsedMessage());
    }
}
