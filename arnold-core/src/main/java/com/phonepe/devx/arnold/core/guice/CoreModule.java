package com.phonepe.devx.arnold.core.guice;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.phonepe.devx.arnold.core.guice.bindings.BindingNames;
import com.phonepe.devx.arnold.core.guice.bindings.OlympusAuthSupplier;
import com.phonepe.devx.arnold.core.services.CibotService;
import com.phonepe.devx.arnold.core.services.ChangeRequestService;
import com.phonepe.devx.arnold.core.services.MergeQueueService;
import com.phonepe.devx.arnold.core.services.GitLabCallbackService;
import com.phonepe.devx.arnold.core.services.CommentService;
import com.phonepe.devx.arnold.core.provider.VersionControlProvider;
import com.phonepe.devx.arnold.core.provider.gitlab.GitLabClient;
import com.phonepe.devx.arnold.core.provider.gitlab.GitLabClientImpl;
import com.phonepe.devx.arnold.core.provider.gitlab.GitLabProvider;
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
import com.phonepe.devx.arnold.core.services.impl.ChangeRequestServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.MergeQueueServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.GitLabCallbackServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.CommentServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.UnavailableAnalysisDispatcher;
import com.phonepe.devx.arnold.core.services.impl.AnalysisServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.VersionControlIntegrationServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.GitLabOAuthServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.VersionControlSyncServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.UserServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.ProjectionServiceImpl;
import com.phonepe.devx.arnold.core.services.impl.ReviewFeedbackServiceImpl;
import com.phonepe.olympus.im.client.OlympusIMBundle;
import com.phonepe.olympus.im.client.OlympusIMClient;
import io.dropwizard.Configuration;
import lombok.AllArgsConstructor;

import javax.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

@AllArgsConstructor
public class CoreModule<T extends Configuration> extends AbstractModule {
    private final OlympusIMBundle<T> olympusIMBundle;

    @Override
    protected void configure() {
        bind(CibotService.class).to(CibotServiceHttpImpl.class);
        bind(UserService.class).to(UserServiceImpl.class);
        bind(ChangeRequestService.class).to(ChangeRequestServiceImpl.class);
        bind(MergeQueueService.class).to(MergeQueueServiceImpl.class);
        bind(GitLabCallbackService.class).to(GitLabCallbackServiceImpl.class);
        bind(CommentService.class).to(CommentServiceImpl.class);
        bind(GitLabClient.class).to(GitLabClientImpl.class);
        bind(GitLabOAuthService.class).to(GitLabOAuthServiceImpl.class);
        Multibinder.newSetBinder(binder(), VersionControlProvider.class)
                .addBinding().to(GitLabProvider.class);
        bind(AnalysisDispatcher.class).to(UnavailableAnalysisDispatcher.class);
        bind(AnalysisOrchestrator.class).to(AnalysisOrchestratorImpl.class);
        bind(AnalysisService.class).to(AnalysisServiceImpl.class);
        bind(VersionControlIntegrationService.class).to(VersionControlIntegrationServiceImpl.class);
        bind(VersionControlSyncService.class).to(VersionControlSyncServiceImpl.class);
        bind(ReviewFeedbackService.class).to(ReviewFeedbackServiceImpl.class);
        bind(ProjectionService.class).to(ProjectionServiceImpl.class);
    }

    @Provides
    @Singleton
    @OlympusAuthSupplier
    public Supplier<String> olympusAuthSupplier() {
        return () -> olympusIMBundle.getOlympusIMClient().getSystemAuthHeader();
    }

    @Provides
    @Singleton
    public OlympusIMClient olympusIMClient() {
        return olympusIMBundle.getOlympusIMClient();
    }

    @Provides
    @Singleton
    @Named(BindingNames.ASYNC_TASK_EXECUTOR)
    public ExecutorService asyncTaskExecutor() {
        return Executors.newCachedThreadPool();
    }
}
