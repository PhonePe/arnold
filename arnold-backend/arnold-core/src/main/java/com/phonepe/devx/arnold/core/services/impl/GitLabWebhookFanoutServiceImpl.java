package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.events.ArnoldDomainEventBus;
import com.phonepe.devx.arnold.core.model.events.ArnoldDomainEvent;
import com.phonepe.devx.arnold.core.model.notification.NotificationType;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.devx.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.services.GitLabWebhookFanoutService;
import com.phonepe.devx.arnold.core.services.MergeQueueService;
import com.phonepe.devx.arnold.core.services.ProviderIdentityService;
import com.phonepe.devx.arnold.core.services.UserMergeRequestService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Singleton
public class GitLabWebhookFanoutServiceImpl implements GitLabWebhookFanoutService {
    private final ProviderIdentityService providerIdentityService;
    private final UserMergeRequestService mergeRequestService;
    private final MergeQueueService mergeQueueService;
    private final ArnoldDomainEventBus eventBus;

    @Inject
    public GitLabWebhookFanoutServiceImpl(final ProviderIdentityService providerIdentityService,
                                          final UserMergeRequestService mergeRequestService,
                                          final MergeQueueService mergeQueueService,
                                          final ArnoldDomainEventBus eventBus) {
        this.providerIdentityService = providerIdentityService;
        this.mergeRequestService = mergeRequestService;
        this.mergeQueueService = mergeQueueService;
        this.eventBus = eventBus;
    }

    @Override
    public void synchronize(final ChangeRequestRecord previous, final ChangeRequestRecord current) {
        final var existing = mergeRequestService.associationsForMergeRequest(current.id());
        final var previousByUser = new HashMap<String, UserChangeRequest>();
        existing.forEach(association -> previousByUser.put(association.userId(), association));

        final var rolesByUser = new HashMap<String, EnumSet<MergeRequestRole>>();
        addRole(rolesByUser, current.authorId(), MergeRequestRole.AUTHOR);
        current.assigneeIds().forEach(providerUserId ->
                addRole(rolesByUser, providerUserId, MergeRequestRole.ASSIGNEE));
        current.reviewerIds().forEach(providerUserId ->
                addRole(rolesByUser, providerUserId, MergeRequestRole.REVIEWER));

        final var affectedUsers = new HashSet<>(previousByUser.keySet());
        affectedUsers.addAll(rolesByUser.keySet());
        final var seenAt = System.currentTimeMillis();
        affectedUsers.forEach(userId -> {
            final var previousAssociation = previousByUser.get(userId);
            final var roles = rolesByUser.getOrDefault(userId, EnumSet.noneOf(MergeRequestRole.class));
            final Set<MergeRequestRole> persistedRoles = !isOpen(current) && roles.isEmpty()
                    && previousAssociation != null ? previousAssociation.roles() : roles;
            final var next = mergeRequestService.upsert(userId, current, persistedRoles, seenAt);
            emitRoleEvents(current, previousAssociation, next);
            mergeQueueService.reconcile(userId, mergeRequestService.associationsForUser(userId));
        });

        emitStateEvents(previous, current, affectedUsers);
    }

    private void addRole(final Map<String, EnumSet<MergeRequestRole>> rolesByUser,
                         final Long providerUserId,
                         final MergeRequestRole role) {
        if (providerUserId == null) {
            return;
        }
        providerIdentityService.findUserId(VersionControlProviderType.GITLAB, Long.toString(providerUserId))
                .ifPresent(userId -> rolesByUser.computeIfAbsent(userId,
                        ignored -> EnumSet.noneOf(MergeRequestRole.class)).add(role));
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

    private void emitStateEvents(final ChangeRequestRecord previous,
                                 final ChangeRequestRecord current,
                                 final Set<String> recipients) {
        if (previous == null || recipients.isEmpty()) {
            return;
        }
        final var previousState = previous.state().toLowerCase(Locale.ROOT);
        final var currentState = current.state().toLowerCase(Locale.ROOT);
        if (!previousState.equals(currentState)) {
            if ("merged".equals(currentState)) {
                publish(current, NotificationType.MERGED, recipients, "Merge request merged", current.title());
            } else if ("closed".equals(currentState)) {
                publish(current, NotificationType.CLOSED, recipients, "Merge request closed", current.title());
            } else if (isOpen(current)) {
                publish(current, NotificationType.REOPENED, recipients, "Merge request reopened", current.title());
            }
        }
        if (blocked(previous) != blocked(current)) {
            publish(current, blocked(current) ? NotificationType.MERGE_BLOCKED : NotificationType.READY_TO_MERGE,
                    recipients, blocked(current) ? "Merge request blocked" : "Ready to merge", current.title());
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

    private boolean isOpen(final ChangeRequestRecord mergeRequest) {
        return "opened".equalsIgnoreCase(mergeRequest.state()) || "open".equalsIgnoreCase(mergeRequest.state());
    }

    private boolean blocked(final ChangeRequestRecord mergeRequest) {
        final var status = mergeRequest.mergeStatus() == null ? ""
                : mergeRequest.mergeStatus().toLowerCase(Locale.ROOT);
        return mergeRequest.hasConflicts() || !mergeRequest.blockingDiscussionsResolved()
                || Set.of("cannot_be_merged", "conflict", "discussions_not_resolved", "not_approved").contains(status);
    }
}
