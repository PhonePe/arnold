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

package com.phonepe.arnold.core.async;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Rejection policy for the async fan-out pool.
 * <p>
 * When the pool is saturated (all threads busy and the queue full) the submitting thread runs the
 * task inline, applying backpressure instead of dropping work -- losing a review-feedback task would
 * mean silent data loss in the ingestion pipeline. This mirrors {@link ThreadPoolExecutor.CallerRunsPolicy}
 * but additionally logs the saturation event, so the (rare) inline execution -- which can briefly
 * block the caller (an HTTP handler) thread -- is observable in metrics/logs rather than invisible.
 * <p>
 * Once the pool is shutting down there is nowhere safe to run the task, so it is dropped with a warning.
 */
@Slf4j
public class LoggingCallerRunsPolicy implements RejectedExecutionHandler {

    @Override
    public void rejectedExecution(final Runnable task, final ThreadPoolExecutor executor) {
        if (executor.isShutdown()) {
            log.warn("Async task executor is shutting down; dropping review-feedback task");
            return;
        }
        log.warn("Async task executor saturated (activeThreads={}, queued={}); running task inline on the "
                        + "caller thread as backpressure",
                executor.getActiveCount(), executor.getQueue().size());
        task.run();
    }
}
