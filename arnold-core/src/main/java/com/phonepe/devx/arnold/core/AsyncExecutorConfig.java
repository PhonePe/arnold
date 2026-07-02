package com.phonepe.devx.arnold.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Tunables for the shared async task executor used to fan out per-comment work
 * (see {@code ReviewFeedbackServiceImpl}). The pool is intentionally bounded so the
 * fan-out can never overflow the downstream Hystrix thread pool and cause rejections.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsyncExecutorConfig {

    /**
     * Number of always-live worker threads.
     */
    private int coreSize = 10;

    /**
     * Upper bound on concurrent worker threads. Keep this at or below the Hystrix
     * {@code CibotServiceHttpImpl.getMergeRequestNote} pool capacity
     * (concurrency + dynamicRequestQueueSize) so fan-out cannot overflow that pool.
     */
    private int maxSize = 20;

    /**
     * Bounded backlog before {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy}
     * applies backpressure to the submitting thread instead of rejecting or spawning threads.
     */
    private int queueCapacity = 512;

    /**
     * Idle timeout (seconds) after which surplus threads are reclaimed.
     */
    private long keepAliveSeconds = 60;
}
