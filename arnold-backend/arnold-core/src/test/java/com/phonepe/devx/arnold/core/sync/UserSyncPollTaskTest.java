package com.phonepe.devx.arnold.core.sync;

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
