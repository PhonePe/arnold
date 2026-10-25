package com.phonepe.arnold.core.sync;

import com.phonepe.arnold.core.cluster.ClusterNodeIdentity;
import com.phonepe.arnold.core.config.UserSyncConfiguration;
import com.phonepe.arnold.core.services.LeaderElectionService;
import com.phonepe.arnold.core.services.UserSyncControlService;
import com.phonepe.arnold.core.services.UserSyncService;
import com.phonepe.arnold.core.storage.UserSyncStateStore;
import io.appform.kaal.KaalScheduler;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Singleton
public class ManagedUserSyncWorker implements Managed {
    private static final long DISPATCH_INTERVAL_MS = 30_000L;
    private static final long KAAL_POLL_INTERVAL_MS = 1_000L;

    private final UserSyncConfiguration configuration;
    private final UserSyncStateStore stateStore;
    private final UserSyncService syncService;
    private final UserSyncControlService controlService;
    private final LeaderElectionService leaderElectionService;
    private final String workerId;
    private KaalScheduler<UserSyncPollTask, Integer> scheduler;
    private ExecutorService schedulerExecutor;
    private ExecutorService workers;

    @Inject
    public ManagedUserSyncWorker(final @Config UserSyncConfiguration configuration,
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
        schedulerExecutor = Executors.newSingleThreadExecutor(runnable -> daemonThread(
                runnable, "arnold-user-sync-scheduler"));
        workers = Executors.newFixedThreadPool(configuration.maxConcurrentUsers(), runnable -> daemonThread(
                runnable, "arnold-user-sync-worker"));
        scheduler = KaalScheduler.<UserSyncPollTask, Integer>builder()
                .withPollingInterval(KAAL_POLL_INTERVAL_MS)
                .withExecutorService(schedulerExecutor)
                .build();
        scheduler.onTaskCompleted().connect("arnold-user-sync-observer", taskData -> {
            if (taskData.getException() != null) {
                log.error("Failed to dispatch due user syncs", taskData.getException());
            } else {
                log.debug("Dispatched {} due user syncs", taskData.getResult());
            }
        });
        scheduler.start();
        scheduler.scheduleNow(new UserSyncPollTask("arnold-user-sync-dispatch", DISPATCH_INTERVAL_MS,
                this::dispatchDue));
    }

    @Override
    public void stop() {
        leaderElectionService.release(UserSyncControlService.LEADER_ELECTION_NAME, workerId);
        if (scheduler != null) {
            scheduler.stop();
        }
        if (schedulerExecutor != null) {
            schedulerExecutor.shutdownNow();
        }
        if (workers != null) {
            workers.shutdownNow();
        }
    }

    private int dispatchDue() {
        if (!controlService.periodicPollingEnabled()) {
            leaderElectionService.release(UserSyncControlService.LEADER_ELECTION_NAME, workerId);
            return 0;
        }
        final var leadership = leaderElectionService.acquireOrRenew(
                UserSyncControlService.LEADER_ELECTION_NAME, workerId, configuration.leaderLeaseMs());
        if (!leadership.leader()) {
            return 0;
        }
        final var due = stateStore.due(System.currentTimeMillis(), configuration.workerBatchSize());
        due.forEach(state -> workers.submit(() -> syncService.process(state, workerId)));
        return due.size();
    }

    private Thread daemonThread(final Runnable runnable, final String name) {
        final var thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }
}
