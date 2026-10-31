package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.services.*;
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
                "devx/test", "Feature", "Description", "url", "feature", "main", "sha", "opened", "not_approved",
                false, 1L, List.of(), List.of(2L, 3L), List.of(), 10, 20);
        when(identities.findUserId(VersionControlProviderType.GITLAB, "2")).thenReturn(Optional.of("reviewer"));
        service.reviewRequested(current, Set.of(2L, 3L, 4L));
        verify(notifications).reviewRequested(current, Set.of("reviewer"));
        verify(identities, never()).findUserId(VersionControlProviderType.GITLAB, "4");
        service.reviewRequested(current.withProjectArchived(true), Set.of(2L));
        verifyNoMoreInteractions(notifications);
    }
}
