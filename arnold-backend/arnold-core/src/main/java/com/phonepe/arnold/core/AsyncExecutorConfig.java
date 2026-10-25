package com.phonepe.arnold.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Tunables for the shared async task executor used to fan out per-comment work
 * (see {@code ReviewFeedbackServiceImpl}). The pool is intentionally bounded so the
 * fan-out can never overwhelm the downstream SCM API.
 * <p>
 * Immutable after Jackson deserialization: {@code @Jacksonized} drives the builder so
 * YAML properties are set at construction time and the object cannot be mutated afterwards.
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsyncExecutorConfig {

    /**
     * Number of always-live worker threads.
     */
    @Builder.Default
    private int coreSize = 10;

    /**
     * Upper bound on concurrent worker threads. Keep this at or below the connection capacity of
     * the SCM metadata client so enrichment fan-out cannot overwhelm the SCM API.
     */
    @Builder.Default
    private int maxSize = 20;

    /**
     * Bounded backlog before {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy}
     * applies backpressure to the submitting thread instead of rejecting or spawning threads.
     */
    @Builder.Default
    private int queueCapacity = 512;

    /**
     * Idle timeout (seconds) after which surplus threads are reclaimed.
     */
    @Builder.Default
    private long keepAliveSeconds = 60;

    /**
     * Maximum time (seconds) to wait for in-flight and queued tasks to drain during a graceful
     * shutdown before the pool is forcibly terminated. Bounds how long a deploy/SIGTERM is held
     * open while still letting outstanding review-feedback ingestion finish. Applied by the
     * lifecycle hook registered in {@code CoreModule#asyncTaskExecutor}.
     */
    @Builder.Default
    private long shutdownAwaitSeconds = 30;
}
