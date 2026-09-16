package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.events.ArnoldSignals;
import com.phonepe.devx.arnold.core.model.events.NotificationRequested;
import com.phonepe.devx.arnold.core.model.notification.NotificationType;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.devx.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangeRequestNotificationPublisherImplTest {
    @Test
    void testRolesChanged_shouldPublishProviderNeutralReviewNotification() {
        final var signals = new ArnoldSignals();
        final var events = new ArrayList<NotificationRequested>();
        signals.subscribe(NotificationRequested.class, "test", events::add);
        final var publisher = new ChangeRequestNotificationPublisherImpl(signals);
        final var changeRequest = changeRequest();
        final var current = new UserChangeRequest("association", "user-1", changeRequest.id(),
                Set.of(MergeRequestRole.REVIEWER), true, true, 1, 2, 10);

        publisher.rolesChanged(changeRequest, null, current);

        assertEquals(1, events.size());
        assertEquals(NotificationType.REVIEW_REQUESTED, events.get(0).type());
        assertEquals(Set.of("user-1"), events.get(0).recipientUserIds());
        assertEquals("/review?provider=GITHUB&repositoryId=org%2Frepo&number=42", events.get(0).deepLink());
    }

    @Test
    void testStateChanged_shouldNotifyReadyOnlyForDetailedMergeableStatus() {
        final var signals = new ArnoldSignals();
        final var events = new ArrayList<NotificationRequested>();
        signals.subscribe(NotificationRequested.class, "test", events::add);
        final var publisher = new ChangeRequestNotificationPublisherImpl(signals);
        final var previous = changeRequest("not_approved");
        final var association = new UserChangeRequest("association", "user-1", previous.id(),
                Set.of(MergeRequestRole.AUTHOR), true, true, 1, 2, 10);
        for (final var status : List.of("can_be_merged", "checking", "ci_must_pass")) {
            publisher.stateChanged(previous, changeRequest(status), List.of(association));
            assertEquals(0, events.size());
        }
        publisher.stateChanged(previous, changeRequest("mergeable"), List.of(association));
        assertEquals(1, events.size());
        assertEquals(NotificationType.READY_TO_MERGE, events.get(0).type());
    }

    private ChangeRequestRecord changeRequest() {
        return changeRequest("can_be_merged");
    }

    private ChangeRequestRecord changeRequest(final String mergeStatus) {
        return new ChangeRequestRecord("github:org/repo:42", VersionControlProviderType.GITHUB, "org/repo",
                "provider-id", 42, "org/repo", "Change request", "Description", "https://example.test/42",
                "feature", "main", "sha", "opened", mergeStatus, false, 1L, List.of(), List.of(2L),
                List.of(), 10, 20);
    }
}
