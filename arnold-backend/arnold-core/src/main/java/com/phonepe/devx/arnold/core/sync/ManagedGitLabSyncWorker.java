package com.phonepe.devx.arnold.core.sync;

import com.phonepe.devx.arnold.core.config.GitLabSyncConfiguration;
import com.phonepe.devx.arnold.core.cluster.ClusterNodeIdentity;
import com.phonepe.devx.arnold.core.services.LeaderElectionService;
import com.phonepe.devx.arnold.core.services.UserSyncControlService;
import com.phonepe.devx.arnold.core.services.UserSyncService;
import com.phonepe.devx.arnold.core.storage.UserSyncStateStore;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class ManagedGitLabSyncWorker implements Managed {
    private final GitLabSyncConfiguration configuration;
    private final UserSyncStateStore stateStore;
    private final UserSyncService syncService;
    private final UserSyncControlService controlService;
    private final LeaderElectionService leaderElectionService;
    private final String workerId;
    private ScheduledExecutorService scheduler;
    private ExecutorService workers;

    @Inject
    public ManagedGitLabSyncWorker(final @Config GitLabSyncConfiguration configuration,
                                   final UserSyncStateStore stateStore,
                                   final UserSyncService syncService,
                                   final UserSyncControlService controlService,
                                   final LeaderElectionService leaderElectionService,
                                   final ClusterNodeIdentity nodeIdentity) {
        this.configuration = configuration;
        this.stateStore = stateStore;
        this.syncService = syncService;
        this.controlService = controlService;
        this.leaderElectionService = leaderElectionService;
        this.workerId = nodeIdentity.id();
    }

    @Override
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final var thread = new Thread(runnable, "arnold-gitlab-sync-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        workers = Executors.newFixedThreadPool(configuration.maxConcurrentUsers(), runnable -> {
            final var thread = new Thread(runnable, "arnold-gitlab-sync-worker");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::dispatchDue, 1, 30, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        leaderElectionService.release(UserSyncControlService.LEADER_ELECTION_NAME, workerId);
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (workers != null) {
            workers.shutdownNow();
        }
    }

    private void dispatchDue() {
        try {
            if (!controlService.periodicPollingEnabled()) {
                leaderElectionService.release(UserSyncControlService.LEADER_ELECTION_NAME, workerId);
                return;
            }
            final var leadership = leaderElectionService.acquireOrRenew(
                    UserSyncControlService.LEADER_ELECTION_NAME, workerId, configuration.leaderLeaseMs());
            if (!leadership.leader()) {
                return;
            }
            stateStore.due(System.currentTimeMillis(), configuration.workerBatchSize())
                    .forEach(state -> workers.submit(() -> syncService.process(state, workerId)));
        } catch (RuntimeException failure) {
            log.error("Failed to dispatch due GitLab syncs", failure);
        }
    }
}
