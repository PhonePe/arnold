package com.phonepe.devx.arnold.core.provider.gitlab;

import com.phonepe.devx.arnold.core.config.GitLabSyncConfiguration;
import com.phonepe.devx.arnold.core.model.sync.UserSyncState;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestSync;
import com.phonepe.devx.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.devx.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.services.ChangeRequestNotificationPublisher;
import com.phonepe.devx.arnold.core.services.ChangeRequestService;
import com.phonepe.devx.arnold.core.services.GitLabOAuthService;
import com.phonepe.devx.arnold.core.services.MergeQueueService;
import com.phonepe.devx.arnold.core.services.ProviderIdentityService;
import com.phonepe.devx.arnold.core.services.UserMergeRequestService;
import com.phonepe.devx.arnold.core.services.UserSyncProvider;
import com.phonepe.devx.arnold.core.services.VersionControlSyncService;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class GitLabUserSyncProvider implements UserSyncProvider {
    private final GitLabSyncConfiguration configuration;
    private final GitLabClient gitLabClient;
    private final GitLabOAuthService oauthService;
    private final ChangeRequestService changeRequestService;
    private final VersionControlSyncService versionControlSyncService;
    private final UserMergeRequestService userMergeRequestService;
    private final MergeQueueService mergeQueueService;
    private final ProviderIdentityService providerIdentityService;
    private final ChangeRequestNotificationPublisher notificationPublisher;

    @Inject
    public GitLabUserSyncProvider(final @Config GitLabSyncConfiguration configuration,
                                  final GitLabClient gitLabClient,
                                  final GitLabOAuthService oauthService,
                                  final ChangeRequestService changeRequestService,
                                  final VersionControlSyncService versionControlSyncService,
                                  final UserMergeRequestService userMergeRequestService,
                                  final MergeQueueService mergeQueueService,
                                  final ProviderIdentityService providerIdentityService,
                                  final ChangeRequestNotificationPublisher notificationPublisher) {
        this.configuration = configuration;
        this.gitLabClient = gitLabClient;
        this.oauthService = oauthService;
        this.changeRequestService = changeRequestService;
        this.versionControlSyncService = versionControlSyncService;
        this.userMergeRequestService = userMergeRequestService;
        this.mergeQueueService = mergeQueueService;
        this.providerIdentityService = providerIdentityService;
        this.notificationPublisher = notificationPublisher;
    }

    @Override
    public VersionControlProviderType type() {
        return VersionControlProviderType.GITLAB;
    }

    @Override
    public void synchronize(final UserSyncState state) {
        final var credential = oauthService.validCredential(state.userId());
        final var providerUserId = Long.parseLong(credential.providerUserId());
        providerIdentityService.link(type(), credential.providerUserId(), state.userId());

        final var firstSync = state.lastSucceededAt() == 0;
        final var historyFrom = firstSync
                ? System.currentTimeMillis() - configuration.historyWindowMs()
                : Math.max(0, state.watermark() - configuration.watermarkOverlapMs());
        final var synchronizedItems = fetch(providerUserId, historyFrom, credential.accessToken());
        final var existing = userMergeRequestService.associationsForUser(state.userId());
        refreshMissingOpenChangeRequests(synchronizedItems, existing, providerUserId, credential.accessToken());

        final var previousRecords = new HashMap<String, ChangeRequestRecord>();
        synchronizedItems.forEach((id, item) -> changeRequestService.record(id)
                .ifPresent(record -> previousRecords.put(id, record)));
        synchronizedItems.values().forEach(item -> versionControlSyncService.synchronize(
                new ChangeRequestSync(item.changeRequest(), action(previousRecords.get(item.changeRequest().id()),
                        item.changeRequest()), null)));

        final var existingById = existing.stream().collect(Collectors.toMap(
                UserChangeRequest::mergeRequestId, value -> value, (left, right) -> right));
        final var seenAt = System.currentTimeMillis();
        synchronizedItems.values().forEach(item -> {
            final var previousAssociation = existingById.get(item.changeRequest().id());
            final var roles = item.roles().isEmpty() && previousAssociation != null && !isOpen(item.changeRequest())
                    ? previousAssociation.roles() : item.roles();
            final var currentAssociation = userMergeRequestService.upsert(
                    state.userId(), item.changeRequest(), roles, seenAt);
            if (!firstSync) {
                notificationPublisher.rolesChanged(item.changeRequest(), previousAssociation, currentAssociation);
            }
        });

        final var refreshedAssociations = userMergeRequestService.associationsForUser(state.userId());
        if (!firstSync) {
            synchronizedItems.values().forEach(item -> notificationPublisher.stateChanged(
                    previousRecords.get(item.changeRequest().id()), item.changeRequest(), refreshedAssociations));
        }
        mergeQueueService.reconcile(state.userId(), refreshedAssociations);
    }

    private Map<String, SyncItem> fetch(final long providerUserId,
                                        final long historyFrom,
                                        final String accessToken) {
        final var synchronizedItems = new LinkedHashMap<String, SyncItem>();
        for (final var role : MergeRequestRole.values()) {
            merge(synchronizedItems, gitLabClient.listMergeRequests(providerUserId, role, "opened", null,
                    accessToken), role);
            merge(synchronizedItems, gitLabClient.listMergeRequests(providerUserId, role, "merged", historyFrom,
                    accessToken), role);
            merge(synchronizedItems, gitLabClient.listMergeRequests(providerUserId, role, "closed", historyFrom,
                    accessToken), role);
        }
        return synchronizedItems;
    }

    private void refreshMissingOpenChangeRequests(final Map<String, SyncItem> synchronizedItems,
                                                  final List<UserChangeRequest> existing,
                                                  final long providerUserId,
                                                  final String accessToken) {
        final var openIds = synchronizedItems.values().stream()
                .filter(item -> isOpen(item.changeRequest()))
                .map(item -> item.changeRequest().id())
                .collect(Collectors.toSet());
        existing.stream()
                .filter(UserChangeRequest::active)
                .filter(UserChangeRequest::open)
                .filter(association -> !openIds.contains(association.mergeRequestId()))
                .forEach(association -> changeRequestService.record(association.mergeRequestId()).ifPresent(current -> {
                    final var refreshed = gitLabClient.getMergeRequest(Long.parseLong(current.repositoryId()),
                            current.number(), accessToken);
                    synchronizedItems.put(refreshed.id(), new SyncItem(refreshed,
                            rolesFor(refreshed, providerUserId)));
                }));
    }

    private void merge(final Map<String, SyncItem> target,
                       final List<ChangeRequestRecord> changeRequests,
                       final MergeRequestRole role) {
        changeRequests.forEach(changeRequest -> target.compute(changeRequest.id(), (id, current) -> {
            final var roles = current == null ? EnumSet.noneOf(MergeRequestRole.class)
                    : EnumSet.copyOf(current.roles());
            roles.add(role);
            final var record = current == null
                    || changeRequest.providerUpdatedAt() >= current.changeRequest().providerUpdatedAt()
                    ? changeRequest : current.changeRequest();
            return new SyncItem(record, roles);
        }));
    }

    private Set<MergeRequestRole> rolesFor(final ChangeRequestRecord changeRequest, final long providerUserId) {
        final var roles = EnumSet.noneOf(MergeRequestRole.class);
        if (changeRequest.authorId() != null && changeRequest.authorId() == providerUserId) {
            roles.add(MergeRequestRole.AUTHOR);
        }
        if (changeRequest.assigneeIds().contains(providerUserId)) {
            roles.add(MergeRequestRole.ASSIGNEE);
        }
        if (changeRequest.reviewerIds().contains(providerUserId)) {
            roles.add(MergeRequestRole.REVIEWER);
        }
        return roles;
    }

    private ChangeRequestSync.Action action(final ChangeRequestRecord previous, final ChangeRequestRecord current) {
        if ("merged".equalsIgnoreCase(current.state())) {
            return ChangeRequestSync.Action.MERGED;
        }
        if ("closed".equalsIgnoreCase(current.state())) {
            return ChangeRequestSync.Action.CLOSED;
        }
        if (previous != null && "closed".equalsIgnoreCase(previous.state()) && isOpen(current)) {
            return ChangeRequestSync.Action.REOPENED;
        }
        return ChangeRequestSync.Action.UPSERT;
    }

    private boolean isOpen(final ChangeRequestRecord changeRequest) {
        return "opened".equalsIgnoreCase(changeRequest.state()) || "open".equalsIgnoreCase(changeRequest.state());
    }

    private record SyncItem(ChangeRequestRecord changeRequest, Set<MergeRequestRole> roles) {
        private SyncItem {
            roles = roles == null ? Set.of() : Set.copyOf(roles);
        }
    }
}
