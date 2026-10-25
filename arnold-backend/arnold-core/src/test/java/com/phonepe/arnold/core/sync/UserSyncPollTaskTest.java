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

package com.phonepe.arnold.core.sync;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserSyncPollTaskTest {
    @Test
    void testTask_shouldRepeatAtConfiguredIntervalAndReturnOperationResult() {
        final var task = new UserSyncPollTask("sync", 30_000, () -> 7);

        assertEquals(30_000, task.delayToNextRun(new Date()));
        assertEquals(7, task.apply(new Date(), null));
    }
}
