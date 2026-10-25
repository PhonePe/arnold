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

public record UserSyncPollTask(
        String id,
        long intervalMs,
        IntSupplier operation
) implements KaalTask<UserSyncPollTask, Integer> {
    @Override
    public long delayToNextRun(final Date currentTime) {
        return intervalMs;
    }

    @Override
    public Integer apply(final Date currentTime, final KaalTaskData<UserSyncPollTask, Integer> taskData) {
        return operation.getAsInt();
    }
}
