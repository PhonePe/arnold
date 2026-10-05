package com.phonepe.devx.arnold.core.guice;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.phonepe.devx.arnold.core.AsyncExecutorConfig;
import com.phonepe.devx.arnold.core.async.LoggingCallerRunsPolicy;
import com.phonepe.devx.arnold.core.guice.bindings.BindingNames;
import com.phonepe.devx.arnold.core.guice.bindings.OlympusAuthSupplier;
import com.phonepe.devx.arnold.core.events.ArnoldSignalPublisher;
import com.phonepe.devx.arnold.core.events.ArnoldSignalSubscriber;
import com.phonepe.devx.arnold.core.events.ArnoldSignals;
import com.phonepe.devx.arnold.core.services.CibotService;
import com.phonepe.devx.arnold.core.services.HousekeepingService;
import com.phonepe.devx.arnold.core.services.PipelineTriggerClient;
import com.phonepe.devx.arnold.core.services.SampleService;
import com.phonepe.devx.arnold.core.services.ChangeRequestService;
import com.phonepe.devx.arnold.core.services.ChangeRequestNotificationPublisher;
import com.phonepe.devx.arnold.core.services.DashboardService;
import com.phonepe.devx.arnold.core.services.MergeQueueService;
import com.phonepe.devx.arnold.core.services.MergeRequestGroupService;
import com.phonepe.devx.arnold.core.services.NotificationEngine;
import com.phonepe.devx.arnold.core.services.NotificationInbox;
import com.phonepe.devx.arnold.core.services.NotificationService;
import com.phonepe.devx.arnold.core.services.PersonaService;
import com.phonepe.devx.arnold.core.services.ProviderIdentityService;
import com.phonepe.devx.arnold.core.services.LeaderElectionService;
import com.phonepe.devx.arnold.core.services.UserSyncControlService;
import com.phonepe.devx.arnold.core.services.StatsService;
import com.phonepe.devx.arnold.core.services.UserMergeRequestService;
import com.phonepe.devx.arnold.core.services.UserSyncService;
import com.phonepe.devx.arnold.core.services.UserSyncProvider;
import com.phonepe.devx.arnold.core.services.GitLabCallbackService;
import com.phonepe.devx.arnold.core.services.GitLabWebhookFanoutService;
import com.phonepe.devx.arnold.core.services.CommentService;
import com.phonepe.devx.arnold.core.provider.VersionControlProvider;
import com.phonepe.devx.arnold.core.provider.gitlab.GitLabClient;
import com.phonepe.devx.arnold.core.provider.gitlab.GitLabClientImpl;
import com.phonepe.devx.arnold.core.provider.gitlab.GitLabProvider;
import com.phonepe.devx.arnold.core.provider.gitlab.GitLabUserSyncProvider;
import com.phonepe.devx.arnold.core.services.AnalysisDispatcher;
import com.phonepe.devx.arnold.core.services.AnalysisService;
import com.phonepe.devx.arnold.core.services.VersionControlIntegrationService;
import com.phonepe.devx.arnold.core.services.GitLabOAuthService;
import com.phonepe.devx.arnold.core.services.VersionControlSyncService;
import com.phonepe.devx.arnold.core.services.ProjectionService;
import com.phonepe.devx.arnold.core.services.ReviewFeedbackService;
import com.phonepe.devx.arnold.core.review.analysis.AnalysisOrchestrator;
import com.phonepe.devx.arnold.core.review.analysis.AnalysisOrchestratorImpl;
import com.phonepe.devx.arnold.core.services.UserService;
import com.phonepe.devx.arnold.core.services.impl.CibotServiceHttpImpl;
import com.phonepe.devx.arnold.core.services.impl.HousekeepingServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.PipelineTriggerClientImpl;
import com.phonepe.devx.arnold.core.services.impl.SampleServiceHttpImpl;
import com.phonepe.devx.arnold.core.services.impl.ChangeRequestServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.DashboardServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.ChangeRequestNotificationPublisherImpl;
import com.phonepe.devx.arnold.core.services.impl.MergeQueueServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.MergeRequestGroupServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.NotificationEngineImpl;
import com.phonepe.devx.arnold.core.services.impl.NotificationInboxImpl;
import com.phonepe.devx.arnold.core.services.impl.NotificationServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.PersonaServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.ProviderIdentityServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.ClusterLeaderElectionServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.UserSyncControlServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.UserSyncServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.StatsServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.UserMergeRequestServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.GitLabCallbackServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.GitLabWebhookFanoutServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.CommentServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.ClioAnalysisDispatcher;
import com.phonepe.devx.arnold.core.services.impl.AnalysisServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.VersionControlIntegrationServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.GitLabOAuthServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.VersionControlSyncServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.UserServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.ProjectionServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.ReviewFeedbackServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.SlackNotificationDeliveryService;
import com.phonepe.devx.arnold.core.services.impl.SlackService;
import com.phonepe.platform.dw.bundle.core.PlatformBundle;
import com.phonepe.olympus.im.client.OlympusIMClient;
import io.dropwizard.Configuration;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import javax.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@AllArgsConstructor
public class CoreModule<T extends Configuration> extends AbstractModule {
    private final PlatformBundle<T> platformBundle;

    @Override
    protected void configure() {
        bind(CibotService.class).to(CibotServiceHttpImpl.class);
        bind(UserService.class).to(UserServiceImpl.class);
        bind(ChangeRequestService.class).to(ChangeRequestServiceImpl.class);
        bind(UserMergeRequestService.class).to(UserMergeRequestServiceImpl.class);
        bind(UserSyncService.class).to(UserSyncServiceImpl.class);
        bind(ChangeRequestNotificationPublisher.class).to(ChangeRequestNotificationPublisherImpl.class);
        bind(ArnoldSignals.class).asEagerSingleton();
        bind(ArnoldSignalPublisher.class).to(ArnoldSignals.class);
        bind(ArnoldSignalSubscriber.class).to(ArnoldSignals.class);
        bind(ProviderIdentityService.class).to(ProviderIdentityServiceImpl.class);
        bind(LeaderElectionService.class).to(ClusterLeaderElectionServiceImpl.class);
        bind(UserSyncControlService.class).to(UserSyncControlServiceImpl.class).asEagerSingleton();
        bind(MergeQueueService.class).to(MergeQueueServiceImpl.class);
        bind(MergeRequestGroupService.class).to(MergeRequestGroupServiceImpl.class);
        bind(NotificationEngine.class).to(NotificationEngineImpl.class).asEagerSingleton();
        bind(NotificationInbox.class).to(NotificationInboxImpl.class);
        bind(NotificationService.class).to(NotificationServiceImpl.class);
        bind(PersonaService.class).to(PersonaServiceImpl.class);
        bind(StatsService.class).to(StatsServiceImpl.class);
        bind(DashboardService.class).to(DashboardServiceImpl.class);
        bind(GitLabCallbackService.class).to(GitLabCallbackServiceImpl.class);
        bind(GitLabWebhookFanoutService.class).to(GitLabWebhookFanoutServiceImpl.class);
        bind(CommentService.class).to(CommentServiceImpl.class);
        bind(GitLabClient.class).to(GitLabClientImpl.class);
        bind(GitLabOAuthService.class).to(GitLabOAuthServiceImpl.class);
        Multibinder.newSetBinder(binder(), VersionControlProvider.class)
                .addBinding().to(GitLabProvider.class);
        Multibinder.newSetBinder(binder(), UserSyncProvider.class)
                .addBinding().to(GitLabUserSyncProvider.class);
        bind(AnalysisDispatcher.class).to(ClioAnalysisDispatcher.class);
        bind(AnalysisOrchestrator.class).to(AnalysisOrchestratorImpl.class);
        bind(AnalysisService.class).to(AnalysisServiceImpl.class);
        bind(VersionControlIntegrationService.class).to(VersionControlIntegrationServiceImpl.class);
        bind(VersionControlSyncService.class).to(VersionControlSyncServiceImpl.class);
        bind(ReviewFeedbackService.class).to(ReviewFeedbackServiceImpl.class);
        bind(HousekeepingService.class).to(HousekeepingServiceImpl.class);
        bind(SampleService.class).to(SampleServiceHttpImpl.class);
        bind(PipelineTriggerClient.class).to(PipelineTriggerClientImpl.class);
        bind(ProjectionService.class).to(ProjectionServiceImpl.class);
        bind(SlackService.class).asEagerSingleton();
        bind(SlackNotificationDeliveryService.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    @OlympusAuthSupplier
    public Supplier<String> olympusAuthSupplier() {
        return () -> platformBundle.getOlympusIMClient().getSystemAuthHeader();
    }

    @Provides
    @Singleton
    public OlympusIMClient olympusIMClient() {
        return platformBundle.getOlympusIMClient();
    }

    @Provides
    @Singleton
    @Named(BindingNames.ASYNC_TASK_EXECUTOR)
    public ExecutorService asyncTaskExecutor(final AsyncExecutorConfig config, final Environment environment) {
        final var threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("arnold-async-%d")
                .setDaemon(true)
                .build();
        // Bounded pool + caller-runs backpressure: fan-out is throttled to maxSize so it can never
        // overflow the downstream Hystrix getMergeRequestNote pool (avoids RejectedExecutionException).
        // When saturated, the submitting thread runs the task inline as backpressure -- see
        // LoggingCallerRunsPolicy, which additionally logs the (rare) saturation event.
        final var executor = new ThreadPoolExecutor(
                config.getCoreSize(),
                config.getMaxSize(),
                config.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getQueueCapacity()),
                threadFactory,
                new LoggingCallerRunsPolicy());
        executor.allowCoreThreadTimeOut(true);

        // Tie the pool to the Dropwizard lifecycle so in-flight/queued tasks get a bounded grace
        // period to drain on deploy/SIGTERM instead of being abandoned when the daemon threads die.
        final long awaitSeconds = config.getShutdownAwaitSeconds();
        environment.lifecycle().manage(new Managed() {
            @Override
            public void start() {
                // The pool is ready as soon as it is constructed; nothing to start.
            }

            @Override
            public void stop() throws InterruptedException {
                log.info("Shutting down async task executor; awaiting up to {}s for in-flight tasks to drain",
                        awaitSeconds);
                executor.shutdown();
                if (executor.awaitTermination(awaitSeconds, TimeUnit.SECONDS)) {
                    log.info("Async task executor drained and shut down cleanly");
                    return;
                }
                final var dropped = executor.shutdownNow();
                log.warn("Async task executor did not drain within {}s; forced shutdown abandoned {} queued task(s)",
                        awaitSeconds, dropped.size());
            }
        });

        return executor;
    }
}
