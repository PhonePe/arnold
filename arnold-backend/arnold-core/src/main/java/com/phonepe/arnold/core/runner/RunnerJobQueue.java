package com.phonepe.arnold.core.runner;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.runner.RunnerJob;
import com.phonepe.arnold.core.storage.RunnerJobRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lease-based job queue for self-hosted runner workers.
 *
 * <p>Claims use optimistic concurrency, so many workers and Arnold nodes can share one queue. A job whose worker stops
 * heartbeating before its lease expires is requeued until {@code maxAttempts} claims have been used, then failed.</p>
 */
@Slf4j
public class RunnerJobQueue {

    private static final int MAX_CAS_ATTEMPTS = 8;

    private final RunnerJobRepository store;
    private final RunnerConfiguration.Local config;
    private final Clock clock;

    public RunnerJobQueue(final RunnerJobRepository store, final RunnerConfiguration.Local config) {
        this(store, config, Clock.systemUTC());
    }

    RunnerJobQueue(final RunnerJobRepository store, final RunnerConfiguration.Local config, final Clock clock) {
        this.store = store;
        this.config = config;
        this.clock = clock;
    }

    public RunnerJob enqueue(final Map<String, String> variables) {
        final var now = clock.millis();
        final var number = now * 1000 + ThreadLocalRandom.current().nextInt(1000);
        final var job = new RunnerJob(RunnerJob.key(number), number, variables.get("WORKFLOW"), Map.copyOf(variables),
                RunnerJob.Status.QUEUED, null, 0, 0, null, now, now);
        store.create(job);
        log.info("Queued runner job: number={}, workflow={}", number, job.workflow());
        return job;
    }

    /**
     * Claims the oldest runnable job for {@code workerId}, recovering expired leases on the way.
     */
    public Optional<RunnerJob> claim(final String workerId) {
        return claim(workerId, null);
    }

    /**
     * Claims a specific job when {@code number} is given (used by CI-dispatched runners), otherwise the oldest
     * runnable job.
     */
    public Optional<RunnerJob> claim(final String workerId, final Long number) {
        if (number != null) {
            return tryClaim(RunnerJob.key(number), workerId);
        }
        final var now = clock.millis();
        final var candidates = store.list().stream()
                .filter(job -> job.status() == RunnerJob.Status.QUEUED
                        || job.status() == RunnerJob.Status.RUNNING && job.leaseExpiresAt() <= now)
                .sorted(Comparator.comparingLong(RunnerJob::createdAt))
                .toList();
        for (final var candidate : candidates) {
            final var claimed = tryClaim(candidate.id(), workerId);
            if (claimed.isPresent()) {
                return claimed;
            }
        }
        return Optional.empty();
    }

    private Optional<RunnerJob> tryClaim(final String id, final String workerId) {
        final var current = store.getWithVersion(id);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        final var job = current.get().value();
        final var now = clock.millis();
        final var expired = job.status() == RunnerJob.Status.RUNNING && job.leaseExpiresAt() <= now;
        if (job.status() != RunnerJob.Status.QUEUED && !expired) {
            return Optional.empty();
        }
        final RunnerJob next = job.attempts() >= config.getMaxAttempts()
                ? job.with(RunnerJob.Status.FAILED, job.workerId(), job.attempts(), 0,
                        "Runner lease expired " + job.attempts() + " time(s)", now)
                : job.with(RunnerJob.Status.RUNNING, workerId, job.attempts() + 1, lease(now), null, now);
        try {
            store.checkAndUpdate(next, current.get().version());
        } catch (ConcurrentUpdateException raced) {
            return Optional.empty();
        }
        if (next.status() == RunnerJob.Status.FAILED) {
            log.warn("Runner job failed after lease expiry: number={}", job.number());
            return Optional.empty();
        }
        log.info("Runner job claimed: number={}, worker={}, attempt={}", job.number(), workerId, next.attempts());
        return Optional.of(next);
    }

    public RunnerJob heartbeat(final long number, final String workerId) {
        return update(number, workerId, job -> job.with(job.status(), workerId, job.attempts(),
                lease(clock.millis()), job.detail(), clock.millis()));
    }

    public RunnerJob complete(final long number, final String workerId, final boolean succeeded, final String detail) {
        final var trimmed = detail == null ? null : detail.substring(0, Math.min(detail.length(), 2000));
        return update(number, workerId, job -> job.with(succeeded ? RunnerJob.Status.SUCCEEDED : RunnerJob.Status.FAILED,
                workerId, job.attempts(), 0, trimmed, clock.millis()));
    }

    private RunnerJob update(final long number, final String workerId,
                             final java.util.function.UnaryOperator<RunnerJob> change) {
        for (var attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            final var current = store.getWithVersion(RunnerJob.key(number))
                    .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
            final var job = current.value();
            if (job.status() != RunnerJob.Status.RUNNING || !workerId.equals(job.workerId())) {
                throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR)
                        .message("Runner job is not leased by this worker").build();
            }
            final var updated = change.apply(job);
            try {
                store.checkAndUpdate(updated, current.version());
                return updated;
            } catch (ConcurrentUpdateException raced) {
                // Re-read and retry against the latest version.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private long lease(final long now) {
        return now + config.getLeaseSeconds() * 1000;
    }
}
