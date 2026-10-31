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

import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.cluster.ClusterNodeIdentity;
import com.phonepe.arnold.core.config.RepositorySyncConfiguration;
import com.phonepe.arnold.core.services.LeaderElectionService;
import com.phonepe.arnold.core.services.RepositorySyncService;
import io.appform.kaal.KaalScheduler;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the repository sync on the elected leader every {@code repositorySync.intervalSeconds}, first shortly after
 * startup.
 */
@Slf4j
@Singleton
public class ManagedRepositorySyncWorker implements Managed {
    private static final long KAAL_POLL_INTERVAL_MS = 1_000L;

    private final RepositorySyncConfiguration configuration;
    private final RepositorySyncService syncService;
    private final LeaderElectionService leaderElectionService;
    private final String workerId;
    private KaalScheduler<UserSyncPollTask, Integer> scheduler;
    private ExecutorService schedulerExecutor;

    @Inject
    public ManagedRepositorySyncWorker(final ArnoldCoreConfiguration configuration,
                                       final RepositorySyncService syncService,
                                       final LeaderElectionService leaderElectionService,
                                       final ClusterNodeIdentity nodeIdentity) {
        this.configuration = configuration.getRepositorySync() == null ? RepositorySyncConfiguration.defaults()
                : configuration.getRepositorySync();
        this.syncService = syncService;
        this.leaderElectionService = leaderElectionService;
        this.workerId = nodeIdentity.id();
    }

    @Override
    public void start() {
        if (!configuration.enabled()) {
            log.info("Repository sync is disabled");
            return;
        }
        schedulerExecutor = Executors.newSingleThreadExecutor(runnable -> {
            final var thread = new Thread(runnable, "arnold-repository-sync-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        scheduler = KaalScheduler.<UserSyncPollTask, Integer>builder()
                .withPollingInterval(KAAL_POLL_INTERVAL_MS)
                .withExecutorService(schedulerExecutor)
                .build();
        scheduler.onTaskCompleted().connect("arnold-repository-sync-observer", taskData -> {
            if (taskData.getException() != null) {
                log.error("Repository sync run failed", taskData.getException());
            }
        });
        scheduler.start();
        scheduler.scheduleAt(new UserSyncPollTask("arnold-repository-sync", configuration.intervalSeconds() * 1000,
                this::runIfLeader), new Date(System.currentTimeMillis() + configuration.initialDelaySeconds() * 1000));
    }

    @Override
    public void stop() {
        if (scheduler == null) {
            return;
        }
        leaderElectionService.release(RepositorySyncService.LEADER_ELECTION_NAME, workerId);
        scheduler.stop();
        schedulerExecutor.shutdownNow();
    }

    int runIfLeader() {
        final var leadership = leaderElectionService.acquireOrRenew(RepositorySyncService.LEADER_ELECTION_NAME,
                workerId, configuration.leaderLeaseMs());
        if (!leadership.leader()) {
            return 0;
        }
        syncService.runOnce();
        return 1;
    }
}
