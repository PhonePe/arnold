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

package com.phonepe.arnold.core.runner;

import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.model.runner.RunnerJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunnerJobQueueTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private InMemoryRunnerJobRepository store;
    private RunnerJobQueue queue;

    @BeforeEach
    void setUp() {
        store = new InMemoryRunnerJobRepository();
        final var config = new RunnerConfiguration.Local();
        config.setLeaseSeconds(60);
        config.setMaxAttempts(2);
        final var clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(final java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(now.get());
            }
        };
        queue = new RunnerJobQueue(store, config, clock);
    }

    @Test
    void claimsOldestQueuedJobAndCompletesIt() {
        final var first = queue.enqueue(Map.of("WORKFLOW", "improve"));
        now.addAndGet(10);
        queue.enqueue(Map.of("WORKFLOW", "smart_diff"));

        final var claimed = queue.claim("w1").orElseThrow();

        assertEquals(first.number(), claimed.number());
        assertEquals(RunnerJob.Status.RUNNING, claimed.status());
        assertEquals(1, claimed.attempts());
        assertEquals("improve", claimed.variables().get("WORKFLOW"));
        final var done = queue.complete(claimed.number(), "w1", true, "ok");
        assertEquals(RunnerJob.Status.SUCCEEDED, done.status());
    }

    @Test
    void onlyTheLeaseHolderCanHeartbeatOrComplete() {
        final var job = queue.enqueue(Map.of("WORKFLOW", "improve"));
        queue.claim("w1").orElseThrow();

        assertThrows(ArnoldError.class, () -> queue.heartbeat(job.number(), "w2"));
        assertThrows(ArnoldError.class, () -> queue.complete(job.number(), "w2", true, null));
        queue.complete(job.number(), "w1", false, "boom");
        assertThrows(ArnoldError.class, () -> queue.complete(job.number(), "w1", true, null));
    }

    @Test
    void expiredLeaseIsReclaimedThenFailsAfterMaxAttempts() {
        final var job = queue.enqueue(Map.of("WORKFLOW", "improve"));
        queue.claim("w1").orElseThrow();
        assertTrue(queue.claim("w2").isEmpty());

        now.addAndGet(61_000);
        final var retried = queue.claim("w2").orElseThrow();
        assertEquals(2, retried.attempts());
        assertThrows(ArnoldError.class, () -> queue.heartbeat(job.number(), "w1"));

        now.addAndGet(61_000);
        assertTrue(queue.claim("w3").isEmpty());
        assertEquals(RunnerJob.Status.FAILED, store.get(job.id()).orElseThrow().status());
    }

    @Test
    void heartbeatExtendsLease() {
        final var job = queue.enqueue(Map.of("WORKFLOW", "improve"));
        queue.claim("w1").orElseThrow();
        now.addAndGet(50_000);
        queue.heartbeat(job.number(), "w1");
        now.addAndGet(50_000);

        assertTrue(queue.claim("w2").isEmpty());
    }

    @Test
    void specificJobCanBeClaimedOnlyOnce() {
        queue.enqueue(Map.of("WORKFLOW", "improve"));
        final var target = queue.enqueue(Map.of("WORKFLOW", "smart_diff"));

        assertEquals(target.number(), queue.claim("ci-1", target.number()).orElseThrow().number());
        assertTrue(queue.claim("ci-2", target.number()).isEmpty());
    }

    @Test
    void concurrentWorkersNeverClaimTheSameJob() throws Exception {
        for (var i = 0; i < 20; i++) {
            now.incrementAndGet();
            queue.enqueue(Map.of("WORKFLOW", "improve"));
        }
        final var claimed = java.util.Collections.synchronizedList(new java.util.ArrayList<Long>());
        final var pool = Executors.newFixedThreadPool(8);
        for (var worker = 0; worker < 8; worker++) {
            final var id = "w" + worker;
            pool.submit(() -> {
                while (true) {
                    final var job = queue.claim(id);
                    if (job.isEmpty() && store.list().stream().noneMatch(j -> j.status() == RunnerJob.Status.QUEUED)) {
                        return;
                    }
                    job.ifPresent(value -> claimed.add(value.number()));
                }
            });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(20, claimed.size());
        assertEquals(20, new HashSet<>(claimed).size());
    }
}
