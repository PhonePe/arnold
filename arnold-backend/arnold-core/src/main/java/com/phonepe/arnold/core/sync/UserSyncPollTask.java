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

import io.appform.kaal.KaalTask;
import io.appform.kaal.KaalTaskData;

import java.util.Date;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * A Kaal task repeating every {@code intervalMs}, measured from when the previous run finished.
 *
 * <p>After a run Kaal schedules the next one at {@code currentTime + delayToNextRun(currentTime)}, where
 * {@code currentTime} is <em>now minus the previous run's start drift</em>. That drift compensation makes Kaal catch
 * up on missed periods: whenever a run starts late (a long previous run, a host that slept, or a VM clock that
 * jumped forward), every following run is scheduled in the past and fires on the next poll until the backlog is
 * gone. The delay returned here adds the drift back so the next run is always {@code intervalMs} after now.</p>
 */
public record UserSyncPollTask(
        String id,
        long intervalMs,
        IntSupplier operation,
        LongSupplier clock
) implements KaalTask<UserSyncPollTask, Integer> {
    public UserSyncPollTask(final String id, final long intervalMs, final IntSupplier operation) {
        this(id, intervalMs, operation, System::currentTimeMillis);
    }

    @Override
    public long delayToNextRun(final Date currentTime) {
        return intervalMs + Math.max(0, clock.getAsLong() - currentTime.getTime());
    }

    @Override
    public Integer apply(final Date currentTime, final KaalTaskData<UserSyncPollTask, Integer> taskData) {
        return operation.getAsInt();
    }
}
