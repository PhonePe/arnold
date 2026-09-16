package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.events.ArnoldSignalPublisher;
import com.phonepe.devx.arnold.core.model.events.NotificationRequested;
import com.phonepe.devx.arnold.core.model.notification.NotificationType;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.devx.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.devx.arnold.core.services.ChangeRequestNotificationPublisher;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class ChangeRequestNotificationPublisherImpl implements ChangeRequestNotificationPublisher {
    private static final String AGGREGATE_TYPE = "change_request";

    private final ArnoldSignalPublisher signals;

    @Inject
    public ChangeRequestNotificationPublisherImpl(final ArnoldSignalPublisher signals) {
        this.signals = signals;
    }

    @Override
    public void rolesChanged(final ChangeRequestRecord changeRequest,
                             final UserChangeRequest previous,
                             final UserChangeRequest current) {
        final var previousRoles = previous == null ? Set.<MergeRequestRole>of() : previous.roles();
        if (!previousRoles.contains(MergeRequestRole.REVIEWER)
                && current.roles().contains(MergeRequestRole.REVIEWER)) {
            publish(changeRequest, NotificationType.REVIEW_REQUESTED, Set.of(current.userId()),
                    "Review requested", changeRequest.title());
        }
        if (!previousRoles.contains(MergeRequestRole.ASSIGNEE)
                && current.roles().contains(MergeRequestRole.ASSIGNEE)) {
            publish(changeRequest, NotificationType.ASSIGNED, Set.of(current.userId()),
                    "Change request assigned", changeRequest.title());
        }
    }

    @Override
    public void stateChanged(final ChangeRequestRecord previous,
                             final ChangeRequestRecord current,
                             final Collection<UserChangeRequest> associations) {
        if (previous == null) {
            return;
        }
        final var recipients = associations.stream()
                .filter(UserChangeRequest::active)
                .filter(item -> current.id().equals(item.mergeRequestId()))
                .map(UserChangeRequest::userId)
                .collect(Collectors.toUnmodifiableSet());
        if (recipients.isEmpty()) {
            return;
        }
        final var previousState = previous.state().toLowerCase(Locale.ROOT);
        final var currentState = current.state().toLowerCase(Locale.ROOT);
        if (!previousState.equals(currentState)) {
            if ("merged".equals(currentState)) {
                publish(current, NotificationType.MERGED, recipients, "Change request merged", current.title());
            } else if ("closed".equals(currentState)) {
                publish(current, NotificationType.CLOSED, recipients, "Change request closed", current.title());
            } else if (isOpen(current)) {
                publish(current, NotificationType.REOPENED, recipients, "Change request reopened", current.title());
            }
        }
        if (isBlocked(previous) != isBlocked(current)) {
            final var blocked = isBlocked(current);
            if (blocked || "mergeable".equalsIgnoreCase(current.mergeStatus()) && !current.draft() && isOpen(current)) {
                publish(current, blocked ? NotificationType.MERGE_BLOCKED : NotificationType.READY_TO_MERGE, recipients,
                        blocked ? "Change request blocked" : "Ready to merge", current.title());
            }
        }
    }

    private void publish(final ChangeRequestRecord changeRequest,
                         final NotificationType type,
                         final Set<String> recipients,
                         final String title,
                         final String body) {
        final var id = type.name().toLowerCase(Locale.ROOT) + ":" + changeRequest.id() + ":"
                + changeRequest.providerUpdatedAt();
        final var deepLink = "/review?provider=" + changeRequest.provider().name()
                + "&repositoryId=" + encode(changeRequest.repositoryId())
                + "&number=" + changeRequest.number();
        signals.publish(new NotificationRequested(id, type, AGGREGATE_TYPE, changeRequest.id(), recipients,
                title, body, deepLink, System.currentTimeMillis()));
    }

    private boolean isOpen(final ChangeRequestRecord changeRequest) {
        return "opened".equalsIgnoreCase(changeRequest.state()) || "open".equalsIgnoreCase(changeRequest.state());
    }

    private boolean isBlocked(final ChangeRequestRecord changeRequest) {
        final var status = changeRequest.mergeStatus() == null ? ""
                : changeRequest.mergeStatus().toLowerCase(Locale.ROOT);
        return changeRequest.hasConflicts() || !changeRequest.blockingDiscussionsResolved()
                || Set.of("cannot_be_merged", "conflict", "discussions_not_resolved", "not_approved").contains(status);
    }

    private String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
