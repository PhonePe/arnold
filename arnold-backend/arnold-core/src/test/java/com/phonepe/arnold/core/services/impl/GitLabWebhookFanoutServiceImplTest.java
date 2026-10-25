/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.*;

class GitLabWebhookFanoutServiceImplTest {
    @Test
    void testReviewRequested_shouldNotifyLinkedCurrentReviewers_whenReviewIsRequestedAgain() {
        final var identities = mock(ProviderIdentityService.class);
        final var notifications = mock(ChangeRequestNotificationPublisher.class);
        final var service = new GitLabWebhookFanoutServiceImpl(identities, mock(UserMergeRequestService.class),
                mock(MergeQueueService.class), notifications);
        final var current = new ChangeRequestRecord("gitlab:10:12", VersionControlProviderType.GITLAB, "10", "100", 12,
                "acme/test", "Feature", "Description", "url", "feature", "main", "sha", "opened", "not_approved",
                false, 1L, List.of(), List.of(2L, 3L), List.of(), 10, 20);
        when(identities.findUserId(VersionControlProviderType.GITLAB, "2")).thenReturn(Optional.of("reviewer"));
        service.reviewRequested(current, Set.of(2L, 3L, 4L));
        verify(notifications).reviewRequested(current, Set.of("reviewer"));
        verify(identities, never()).findUserId(VersionControlProviderType.GITLAB, "4");
        service.reviewRequested(current.withProjectArchived(true), Set.of(2L));
        verifyNoMoreInteractions(notifications);
    }
}
