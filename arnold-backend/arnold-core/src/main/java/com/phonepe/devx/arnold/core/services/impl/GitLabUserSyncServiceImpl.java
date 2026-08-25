package com.phonepe.devx.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.devx.arnold.core.config.GitLabSyncConfiguration;
import com.phonepe.devx.arnold.core.cluster.ClusterNodeIdentity;
import com.phonepe.devx.arnold.core.events.ArnoldDomainEventBus;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.events.ArnoldDomainEvent;
import com.phonepe.devx.arnold.core.model.notification.NotificationType;
import com.phonepe.devx.arnold.core.model.sync.SyncStatus;
import com.phonepe.devx.arnold.core.model.sync.UserSyncState;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestSync;
import com.phonepe.devx.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.devx.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.guice.bindings.BindingNames;
import com.phonepe.devx.arnold.core.provider.gitlab.GitLabClient;
import com.phonepe.devx.arnold.core.services.ChangeRequestService;
import com.phonepe.devx.arnold.core.services.GitLabOAuthService;
import com.phonepe.devx.arnold.core.services.MergeQueueService;
import com.phonepe.devx.arnold.core.services.ProviderIdentityService;
import com.phonepe.devx.arnold.core.services.UserMergeRequestService;
import com.phonepe.devx.arnold.core.services.UserSyncScheduler;
import com.phonepe.devx.arnold.core.services.UserSyncService;
import com.phonepe.devx.arnold.core.services.VersionControlSyncService;
import com.phonepe.devx.arnold.core.storage.UserSyncStateStore;
import com.phonepe.devx.arnold.models.sync.UserSyncStatusResponse;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ExecutorService;

/**
 * UserSyncService implementation for GitLab. This service is responsible for synchronizing user data with GitLab,
 * including merge requests and their associated roles. It handles scheduling, retrying, and processing of
 * synchronization tasks, as well as emitting relevant events for changes in merge request states and roles.
 * <p>
 * The service interacts with various components such as GitLabClient for API calls, replaceable storage and identity
 * service abstractions for state management, and an event bus for publishing domain events. It ensures that user data
 * is kept up-to-date with GitLab, and provides status information about the synchronization process.
 *
 */
@Singleton
public class GitLabUserSyncServiceImpl implements UserSyncService {
    private static final int MAX_STATE_UPDATE_ATTEMPTS = 8;

    private final GitLabSyncConfiguration configuration;
    private final GitLabClient gitLabClient;
    private final GitLabOAuthService oauthService;
    private final ChangeRequestService changeRequestService;
    private final VersionControlSyncService versionControlSyncService;
    private final UserMergeRequestService userMergeRequestService;
    private final MergeQueueService mergeQueueService;
    private final UserSyncScheduler scheduler;
    private final UserSyncStateStore stateStore;
    private final ProviderIdentityService providerIdentityService;
    private final ArnoldDomainEventBus eventBus;
    private final ExecutorService asyncTaskExecutor;
    private final String nodeId;

    @Inject
    public GitLabUserSyncServiceImpl(final @Config GitLabSyncConfiguration configuration,
                                     final GitLabClient gitLabClient,
                                     final GitLabOAuthService oauthService,
                                     final ChangeRequestService changeRequestService,
                                     final VersionControlSyncService versionControlSyncService,
                                     final UserMergeRequestService userMergeRequestService,
                                     final MergeQueueService mergeQueueService,
                                     final UserSyncScheduler scheduler,
                                     final UserSyncStateStore stateStore,
                                     final ProviderIdentityService providerIdentityService,
                                     final ArnoldDomainEventBus eventBus,
                                     final @Named(BindingNames.ASYNC_TASK_EXECUTOR) ExecutorService asyncTaskExecutor,
                                     final ClusterNodeIdentity nodeIdentity) {
        this.configuration = configuration;
        this.gitLabClient = gitLabClient;
        this.oauthService = oauthService;
        this.changeRequestService = changeRequestService;
        this.versionControlSyncService = versionControlSyncService;
        this.userMergeRequestService = userMergeRequestService;
        this.mergeQueueService = mergeQueueService;
        this.scheduler = scheduler;
        this.stateStore = stateStore;
        this.providerIdentityService = providerIdentityService;
        this.eventBus = eventBus;
        this.asyncTaskExecutor = asyncTaskExecutor;
        this.nodeId = nodeIdentity.id();
    }

    @Override
    public UserSyncStatusResponse ensureScheduled(final String userId) {
        final var state = scheduler.ensureScheduled(userId);
        if (state.lastSucceededAt() == 0 && state.status() != SyncStatus.RUNNING) {
            dispatchOnDemand(state);
        }
        return response(state);
    }

    @Override
    public UserSyncStatusResponse retry(final String userId) {
        final var state = scheduler.retry(userId);
        if (state.status() != SyncStatus.RUNNING) {
            dispatchOnDemand(state);
        }
        return response(state);
    }

    @Override
    public UserSyncStatusResponse status(final String userId) {
        return response(stateStore.get(UserSyncState.key(VersionControlProviderType.GITLAB, userId))
                .orElseGet(() -> UserSyncState.neverSynced(VersionControlProviderType.GITLAB, userId)));
    }

    @Override
    public void cancel(final String userId) {
        scheduler.cancel(userId);
    }

    @Override
    public void process(final UserSyncState dueState, final String workerId) {
        final var claimed = claim(dueState.id(), workerId);
        if (claimed == null) {
            return;
        }
        try {
            synchronize(claimed);
            complete(claimed.id());
        } catch (RuntimeException failure) {
            fail(claimed.id(), failure);
        }
    }

    private void dispatchOnDemand(final UserSyncState state) {
        asyncTaskExecutor.execute(() -> process(state, nodeId + ":on-demand"));
    }

    private void synchronize(final UserSyncState state) {
        final var credential = oauthService.validCredential(state.userId());
        final var providerUserId = Long.parseLong(credential.providerUserId());
        providerIdentityService.link(VersionControlProviderType.GITLAB, credential.providerUserId(), state.userId());

        final var firstSync = state.lastSucceededAt() == 0;
        final var historyFrom = firstSync
                ? System.currentTimeMillis() - configuration.historyWindowMs()
                : Math.max(0, state.watermark() - configuration.watermarkOverlapMs());

        // Fetch merge requests from GitLab for the user, including opened, merged, and closed states, and merge them into a synchronized collection.
        final var synchronizedItems = new LinkedHashMap<String, SyncItem>();
        for (final var role : MergeRequestRole.values()) {
            merge(synchronizedItems, gitLabClient.listMergeRequests(providerUserId, role, "opened", null,
                    credential.accessToken()), role);
            merge(synchronizedItems, gitLabClient.listMergeRequests(providerUserId, role, "merged", historyFrom,
                    credential.accessToken()), role);
            merge(synchronizedItems, gitLabClient.listMergeRequests(providerUserId, role, "closed", historyFrom,
                    credential.accessToken()), role);
        }

        // For each existing association of the user with merge requests, check if the merge request is still open. If not, fetch the latest state from GitLab and update the synchronized collection accordingly.
        final var existing = userMergeRequestService.associationsForUser(state.userId());
        final var openIds = synchronizedItems.values().stream().filter(item -> isOpen(item.mergeRequest()))
                .map(item -> item.mergeRequest().id()).collect(java.util.stream.Collectors.toSet());
        for (final var association : existing) {
            if (association.active() && association.open() && !openIds.contains(association.mergeRequestId())) {
                final var current = changeRequestService.record(association.mergeRequestId()).orElse(null);
                if (current == null) {
                    continue;
                }
                final var refreshed = gitLabClient.getMergeRequest(Long.parseLong(current.repositoryId()),
                        current.number(), credential.accessToken());
                synchronizedItems.put(refreshed.id(), new SyncItem(refreshed,
                        rolesFor(refreshed, providerUserId)));
            }
        }

        final var beforeById = new HashMap<String, ChangeRequestRecord>();
        synchronizedItems.forEach((id, item) -> changeRequestService.record(id)
                .ifPresent(record -> beforeById.put(id, record)));

        synchronizedItems.values().forEach(item -> versionControlSyncService.synchronize(
                new ChangeRequestSync(item.mergeRequest(), action(beforeById.get(item.mergeRequest().id()),
                        item.mergeRequest()), null)));

        final var existingById = existing.stream().collect(java.util.stream.Collectors.toMap(
                UserChangeRequest::mergeRequestId, value -> value, (left, right) -> right));
        final var seenAt = System.currentTimeMillis();
        synchronizedItems.values().forEach(item -> {
            final var previousAssociation = existingById.get(item.mergeRequest().id());
            final var nextAssociation = item.roles().isEmpty() && previousAssociation != null
                    && !isOpen(item.mergeRequest())
                    ? userMergeRequestService.upsert(state.userId(), item.mergeRequest(),
                            previousAssociation.roles(), seenAt)
                    : userMergeRequestService.upsert(state.userId(), item.mergeRequest(), item.roles(), seenAt);
            if (!firstSync) {
                emitRoleEvents(item.mergeRequest(), previousAssociation, nextAssociation);
            }
        });

        final var refreshedAssociations = userMergeRequestService.associationsForUser(state.userId());
        if (!firstSync) {
            synchronizedItems.values().forEach(item -> emitMergeRequestEvents(beforeById.get(item.mergeRequest().id()),
                    item.mergeRequest(), refreshedAssociations));
        }
        mergeQueueService.reconcile(state.userId(), refreshedAssociations);
    }

    // locked updated on SyncStatus RUNNING for that user
    private UserSyncState claim(final String id, final String workerId) {
        final var current = stateStore.getVersioned(id).orElse(null);
        if (current == null || current.value().nextDueAt() > System.currentTimeMillis()
                || current.value().status() == SyncStatus.RUNNING
                && current.value().leaseUntil() > System.currentTimeMillis()) {
            return null;
        }
        final var now = System.currentTimeMillis();
        final var running = new UserSyncState(id, current.value().userId(), current.value().provider(),
                SyncStatus.RUNNING, now, current.value().lastSucceededAt(), current.value().nextDueAt(),
                current.value().watermark(), workerId, now + configuration.leaseMs(), null,
                current.value().consecutiveFailures());
        try {
            stateStore.update(running, current.version());
            return running;
        } catch (ConcurrentUpdateException conflict) {
            return null;
        }
    }

    private void complete(final String id) {
        updateState(id, current -> {
            final var now = System.currentTimeMillis();
            final var jitter = ThreadLocalRandom.current().nextLong(0, 60_001);
            return new UserSyncState(id, current.userId(), current.provider(), SyncStatus.SUCCEEDED,
                    current.lastAttemptAt(), now, now + configuration.intervalMs() + jitter, now, null, 0,
                    null, 0);
        });
    }

    private void fail(final String id, final RuntimeException failure) {
        updateState(id, current -> {
            final var failures = current.consecutiveFailures() + 1;
            final var exponent = Math.min(4, Math.max(0, failures - 1));
            final var backoff = Math.min(configuration.intervalMs(), 60_000L * (1L << exponent));
            final var message = (failure.getMessage() == null ? failure.getClass().getSimpleName()
                    : failure.getMessage());
            return new UserSyncState(id, current.userId(), current.provider(), SyncStatus.FAILED,
                    current.lastAttemptAt(), current.lastSucceededAt(), System.currentTimeMillis() + backoff,
                    current.watermark(), null, 0, message.substring(0, Math.min(500, message.length())), failures);
        });
    }

    private void updateState(final String id, final java.util.function.UnaryOperator<UserSyncState> update) {
        for (var attempt = 0; attempt < MAX_STATE_UPDATE_ATTEMPTS; attempt++) {
            final var current = stateStore.getVersioned(id).orElseThrow();
            try {
                stateStore.update(update.apply(current.value()), current.version());
                return;
            } catch (ConcurrentUpdateException conflict) {
                // Re-read and retry.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private void merge(final Map<String, SyncItem> target,
                       final List<ChangeRequestRecord> mergeRequests,
                       final MergeRequestRole role) {
        mergeRequests.forEach(mergeRequest -> target.compute(mergeRequest.id(), (id, current) -> {
            final var roles = current == null ? EnumSet.noneOf(MergeRequestRole.class)
                    : EnumSet.copyOf(current.roles());
            roles.add(role);
            final var record = current == null
                    || mergeRequest.providerUpdatedAt() >= current.mergeRequest().providerUpdatedAt()
                    ? mergeRequest : current.mergeRequest();
            return new SyncItem(record, roles);
        }));
    }

    private Set<MergeRequestRole> rolesFor(final ChangeRequestRecord mergeRequest, final long providerUserId) {
        final var roles = EnumSet.noneOf(MergeRequestRole.class);
        if (mergeRequest.authorId() != null && mergeRequest.authorId() == providerUserId) {
            roles.add(MergeRequestRole.AUTHOR);
        }
        if (mergeRequest.assigneeIds().contains(providerUserId)) {
            roles.add(MergeRequestRole.ASSIGNEE);
        }
        if (mergeRequest.reviewerIds().contains(providerUserId)) {
            roles.add(MergeRequestRole.REVIEWER);
        }
        return roles;
    }

    private void emitRoleEvents(final ChangeRequestRecord mergeRequest,
                                final UserChangeRequest previous,
                                final UserChangeRequest next) {
        final var previousRoles = previous == null ? Set.<MergeRequestRole>of() : previous.roles();
        if (!previousRoles.contains(MergeRequestRole.REVIEWER) && next.roles().contains(MergeRequestRole.REVIEWER)) {
            publish(mergeRequest, NotificationType.REVIEW_REQUESTED, Set.of(next.userId()),
                    "Review requested", mergeRequest.title());
        }
        if (!previousRoles.contains(MergeRequestRole.ASSIGNEE) && next.roles().contains(MergeRequestRole.ASSIGNEE)) {
            publish(mergeRequest, NotificationType.ASSIGNED, Set.of(next.userId()),
                    "Merge request assigned", mergeRequest.title());
        }
    }

    private void emitMergeRequestEvents(final ChangeRequestRecord previous,
                                        final ChangeRequestRecord next,
                                        final List<UserChangeRequest> associations) {
        if (previous == null) {
            return;
        }
        final var recipients = associations.stream()
                .filter(UserChangeRequest::active)
                .filter(item -> next.id().equals(item.mergeRequestId()))
                .map(UserChangeRequest::userId).collect(java.util.stream.Collectors.toSet());
        if (recipients.isEmpty()) {
            return;
        }
        final var previousState = previous.state().toLowerCase(Locale.ROOT);
        final var nextState = next.state().toLowerCase(Locale.ROOT);
        if (!previousState.equals(nextState)) {
            if ("merged".equals(nextState)) {
                publish(next, NotificationType.MERGED, recipients, "Merge request merged", next.title());
            } else if ("closed".equals(nextState)) {
                publish(next, NotificationType.CLOSED, recipients, "Merge request closed", next.title());
            } else if ("opened".equals(nextState)) {
                publish(next, NotificationType.REOPENED, recipients, "Merge request reopened", next.title());
            }
        }
        final var wasBlocked = blocked(previous);
        final var isBlocked = blocked(next);
        if (wasBlocked != isBlocked) {
            publish(next, isBlocked ? NotificationType.MERGE_BLOCKED : NotificationType.READY_TO_MERGE, recipients,
                    isBlocked ? "Merge request blocked" : "Ready to merge", next.title());
        }
    }

    private void publish(final ChangeRequestRecord mergeRequest,
                         final NotificationType type,
                         final Set<String> recipients,
                         final String title,
                         final String body) {
        final var id = type.name().toLowerCase(Locale.ROOT) + ":" + mergeRequest.id() + ":"
                + mergeRequest.providerUpdatedAt();
        final var deepLink = "/review?provider=GITLAB&projectId=" + mergeRequest.repositoryId()
                + "&iid=" + mergeRequest.number();
        eventBus.publish(new ArnoldDomainEvent(id, type, mergeRequest.id(), recipients, title, body, deepLink,
                System.currentTimeMillis()));
    }

    private ChangeRequestSync.Action action(final ChangeRequestRecord previous, final ChangeRequestRecord next) {
        if ("merged".equalsIgnoreCase(next.state())) {
            return ChangeRequestSync.Action.MERGED;
        }
        if ("closed".equalsIgnoreCase(next.state())) {
            return ChangeRequestSync.Action.CLOSED;
        }
        if (previous != null && "closed".equalsIgnoreCase(previous.state()) && isOpen(next)) {
            return ChangeRequestSync.Action.REOPENED;
        }
        return ChangeRequestSync.Action.UPSERT;
    }

    private boolean isOpen(final ChangeRequestRecord mergeRequest) {
        return "opened".equalsIgnoreCase(mergeRequest.state()) || "open".equalsIgnoreCase(mergeRequest.state());
    }

    private boolean blocked(final ChangeRequestRecord mergeRequest) {
        final var status = mergeRequest.mergeStatus() == null ? ""
                : mergeRequest.mergeStatus().toLowerCase(Locale.ROOT);
        return mergeRequest.hasConflicts() || !mergeRequest.blockingDiscussionsResolved()
                || Set.of("cannot_be_merged", "conflict", "discussions_not_resolved", "not_approved").contains(status);
    }

    private UserSyncStatusResponse response(final UserSyncState state) {
        return new UserSyncStatusResponse(state.provider().name(), state.status().name(), state.lastAttemptAt(),
                state.lastSucceededAt(), state.nextDueAt(), state.failure(), state.status() == SyncStatus.FAILED);
    }

    private record SyncItem(ChangeRequestRecord mergeRequest, Set<MergeRequestRole> roles) {
        private SyncItem {
            roles = roles == null ? Set.of() : Set.copyOf(roles);
        }
    }
}
