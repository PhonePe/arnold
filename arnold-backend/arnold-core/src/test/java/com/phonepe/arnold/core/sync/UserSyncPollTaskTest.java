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

import io.appform.kaal.KaalScheduler;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserSyncPollTaskTest {
    @Test
    void testTask_shouldRepeatAtConfiguredIntervalAndReturnOperationResult() {
        final var task = new UserSyncPollTask("sync", 30_000, () -> 7, () -> 1_000_000L);

        assertEquals(30_000, task.delayToNextRun(new Date(1_000_000L)));
        assertEquals(7, task.apply(new Date(), null));
    }

    @Test
    void testDelayToNextRun_shouldScheduleOneIntervalFromNow_whenKaalCompensatesForDrift() {
        final var now = 10_000_000L;
        final var task = new UserSyncPollTask("sync", 300_000, () -> 1, () -> now);
        // Kaal passes now - drift as the reference time; a run that started an hour late has a one-hour drift.
        final var reference = new Date(now - 3_600_000L);

        final var nextRunAt = reference.getTime() + task.delayToNextRun(reference);

        assertEquals(now + 300_000, nextRunAt);
    }

    @Test
    void testScheduler_shouldNotCatchUpMissedRuns_whenFirstRunStartsLate() throws Exception {
        final var runs = new AtomicInteger();
        final var executor = Executors.newSingleThreadExecutor();
        final var scheduler = KaalScheduler.<UserSyncPollTask, Integer>builder()
                .withPollingInterval(20)
                .withExecutorService(executor)
                .build();
        scheduler.start();
        try {
            scheduler.scheduleAt(new UserSyncPollTask("sync", 60_000, runs::incrementAndGet),
                    new Date(System.currentTimeMillis() - 3_600_000L));
            Thread.sleep(600);

            assertEquals(1, runs.get());
        } finally {
            scheduler.stop();
            executor.shutdownNow();
        }
    }
}
