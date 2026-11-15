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

package com.phonepe.arnold.core.resources;

import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.core.services.HousekeepingService;
import com.phonepe.arnold.models.es.BackfillResult;
import com.phonepe.arnold.models.request.BackfillRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class HousekeepingTest {

    @Mock
    HousekeepingService housekeepingService;

    private Housekeeping housekeeping;

    @BeforeEach
    void init() {
        MockitoAnnotations.initMocks(this);
        housekeeping = new Housekeeping(housekeepingService);
    }

    @Test
    @DisplayName("Should return 'prefix + message' string")
    void testSync() {
        String prefix = "Hello";
        String message = "JUnit5";
        ArnoldResponse<String> response = housekeeping.sync(prefix, message);
        assertEquals(String.format("%s %s", prefix, message), response.getResponse());
    }

    @Test
    @DisplayName("Should return default message when message query param is not provided")
    void testSyncDefaultMessage() {
        String prefix = "Hello";
        ArnoldResponse<String> response = housekeeping.sync(prefix, null);
        assertEquals(String.format("%s null", prefix), response.getResponse());
    }

    @Test
    @DisplayName("Should delegate backfill to housekeepingService")
    void testBackfill() {
        final BackfillResult expected = BackfillResult.builder()
                .dryRun(true)
                .scanned(5)
                .enrichable(3)
                .build();
        when(housekeepingService.backfillReviewFeedback(any(BackfillRequest.class))).thenReturn(expected);
        ArnoldResponse<BackfillResult> response =
                housekeeping.backfillReviewFeedback(BackfillRequest.builder().build());
        assertEquals(expected, response.getResponse());
    }
}
