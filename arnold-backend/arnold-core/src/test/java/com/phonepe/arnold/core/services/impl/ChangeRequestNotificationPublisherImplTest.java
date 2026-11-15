/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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

package com.phonepe.arnold.core.services.impl;

import com.phonepe.arnold.core.events.ArnoldSignals;
import com.phonepe.arnold.core.model.events.NotificationRequested;
import com.phonepe.arnold.core.model.notification.NotificationType;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
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
        assertEquals("/review/GITHUB/org%2Frepo/42", events.get(0).deepLink());
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

    @Test
    void testStateChanged_shouldNotifyReady_whenCiOrChecksFinish() {
        final var signals = new ArnoldSignals();
        final var events = new ArrayList<NotificationRequested>();
        signals.subscribe(NotificationRequested.class, "test", events::add);
        final var publisher = new ChangeRequestNotificationPublisherImpl(signals);
        final var ready = changeRequest("mergeable");
        final var association = new UserChangeRequest("association", "user-1", ready.id(),
                Set.of(MergeRequestRole.AUTHOR), true, true, 1, 2, 10);
        for (final var status : List.of("ci_still_running", "checking", "preparing", "unchecked",
                "ci_must_pass", "status_checks_must_pass", "requested_changes")) {
            events.clear();
            publisher.stateChanged(changeRequest(status), ready, List.of(association));
            assertEquals(List.of(NotificationType.READY_TO_MERGE), events.stream().map(NotificationRequested::type).toList());
        }
        events.clear();
        publisher.stateChanged(ready, ready, List.of(association));
        publisher.stateChanged(ready, ready.withProjectArchived(true), List.of(association));
        assertEquals(0, events.size());
    }

    @Test
    void testReviewRequested_shouldNotifyOnlyRequestedUsers_whenReviewIsRequestedAgain() {
        final var signals = new ArnoldSignals();
        final var events = new ArrayList<NotificationRequested>();
        signals.subscribe(NotificationRequested.class, "test", events::add);
        final var publisher = new ChangeRequestNotificationPublisherImpl(signals);
        publisher.reviewRequested(changeRequest(), Set.of("reviewer"));
        publisher.reviewRequested(changeRequest(), Set.of());
        assertEquals(1, events.size());
        assertEquals(NotificationType.REVIEW_REQUESTED, events.get(0).type());
        assertEquals(Set.of("reviewer"), events.get(0).recipientUserIds());
    }

    @Test
    void testIsBlocked_shouldRecognizeGitLabBlockers_whenDetailedStatusIsPresent() {
        for (final var status : List.of("ci_must_pass", "ci_still_running", "commits_status", "draft_status",
                "jira_association_missing", "merge_request_blocked", "merge_time", "need_rebase", "not_approved",
                "requested_changes", "security_policy_pipeline_check", "security_policy_violations",
                "status_checks_must_pass", "locked_paths", "locked_lfs_files", "title_regex")) {
            org.junit.jupiter.api.Assertions.assertTrue(changeRequest(status).isBlocked(), status);
        }
        for (final var status : List.of("checking", "preparing", "unchecked", "approvals_syncing", "mergeable")) {
            org.junit.jupiter.api.Assertions.assertFalse(changeRequest(status).isBlocked(), status);
        }
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
