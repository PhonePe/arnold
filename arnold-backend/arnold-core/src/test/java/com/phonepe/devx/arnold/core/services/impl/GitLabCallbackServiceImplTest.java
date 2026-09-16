package com.phonepe.devx.arnold.core.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.devx.arnold.core.config.GitLabSyncConfiguration;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestSync;
import com.phonepe.devx.arnold.core.services.VersionControlSyncService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeWebhookDeliveryStore;
import com.phonepe.devx.arnold.models.callback.GitLabWebhookRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GitLabCallbackServiceImplTest {
    @Test
    void testProcess_shouldUseDetailedStatusAndProviderDates_whenWebhookReportsCoarseMergeability() throws Exception {
        for (final var status : List.of("not_approved", "ci_must_pass", "mergeable", "")) {
            final var sync = mock(VersionControlSyncService.class);
            final var service = new GitLabCallbackServiceImpl(new GitLabSyncConfiguration("secret"),
                    mock(AerospikeWebhookDeliveryStore.class), sync);
            final var request = new ObjectMapper().readValue("""
                    {"object_kind":"merge_request", "project":{"id":17352,"path_with_namespace":"devx/clio"},
                     "object_attributes":{"id":906419,"iid":86,"title":"Report Improve execution status to Arnold",
                       "state":"opened", "merge_status":"can_be_merged", "detailed_merge_status":"%s",
                       "created_at":"2026-09-10 10:00:00 UTC", "updated_at":"2026-09-16 11:32:44.123456 UTC"}}
                    """.formatted(status), GitLabWebhookRequest.class);

            service.process("secret", "delivery", "Merge Request Hook", request);

            final var event = ArgumentCaptor.forClass(ChangeRequestSync.class);
            verify(sync).synchronize(event.capture());
            final var mr = event.getValue().changeRequest();
            assertEquals(status.isEmpty() ? "can_be_merged" : status, mr.mergeStatus());
            assertEquals(Instant.parse("2026-09-10T10:00:00Z").toEpochMilli(), mr.createdAt());
            assertEquals(Instant.parse("2026-09-16T11:32:44.123456Z").toEpochMilli(), mr.providerUpdatedAt());
        }
    }
}
