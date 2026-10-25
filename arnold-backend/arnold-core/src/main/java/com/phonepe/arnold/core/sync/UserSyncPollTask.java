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
