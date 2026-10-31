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
package com.phonepe.arnold.core.provider.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.arnold.core.config.GitHubConfiguration;
import com.phonepe.arnold.core.model.sync.WebhookDelivery;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.ChangeRequestSync;
import com.phonepe.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.ChangeRequestService;
import com.phonepe.arnold.core.services.GitLabWebhookFanoutService;
import com.phonepe.arnold.core.services.MergeQueueService;
import com.phonepe.arnold.core.services.ProviderIdentityService;
import com.phonepe.arnold.core.services.UserMergeRequestService;
import com.phonepe.arnold.core.services.VersionControlSyncService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeWebhookDeliveryStore;
import com.phonepe.arnold.core.sync.RepositoryChangeRequestImporter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubWebhookImportTest {
    private static final byte[] OPENED = """
            {"action":"opened","pull_request":{"id":556,"number":8,"title":"New","state":"open",
             "merged_at":null,"html_url":"https://github.com/acme/api/pull/8",
             "created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z",
             "user":{"id":1,"login":"alice"},"assignees":[{"id":2,"login":"bob"}],
             "requested_reviewers":[{"id":3,"login":"carol"}],"labels":[],
             "head":{"ref":"feature","sha":"head"},"base":{"ref":"main","sha":"base",
             "repo":{"id":42,"full_name":"acme/api"}}}}""".getBytes(StandardCharsets.UTF_8);

    @Test
    void createsUnknownPullRequestAndLinksArnoldUsers() throws Exception {
        final var config = new GitHubConfiguration();
        config.setWebhookSecret("webhook-secret");
        final var deliveries = mock(AerospikeWebhookDeliveryStore.class);
        final var changeRequests = mock(ChangeRequestService.class);
        final var sync = mock(VersionControlSyncService.class);
        final var userMergeRequests = mock(UserMergeRequestService.class);
        final var identities = mock(ProviderIdentityService.class);
        final var mergeQueue = mock(MergeQueueService.class);
        final var fanout = mock(GitLabWebhookFanoutService.class);
        when(deliveries.getWithVersion("github:d9")).thenReturn(Optional.empty(),
                Optional.of(Versioned.of(new WebhookDelivery("github:d9", "pull_request", "processing", 1, 0), 1L)));
        when(changeRequests.record(anyString())).thenReturn(Optional.empty());
        when(sync.synchronize(any(ChangeRequestSync.class))).thenReturn(true);
        when(identities.findUserId(any(), anyString())).thenReturn(Optional.empty());
        when(identities.findUserId(VersionControlProviderType.GITHUB, "1")).thenReturn(Optional.of("alice-arnold"));
        when(identities.findUserId(VersionControlProviderType.GITHUB, "3")).thenReturn(Optional.of("carol-arnold"));
        final List<UserChangeRequest> carolAssociations = List.of();
        when(userMergeRequests.associationsForUser("carol-arnold")).thenReturn(carolAssociations);
        final var importer = new RepositoryChangeRequestImporter(changeRequests, sync, userMergeRequests, identities,
                mergeQueue);
        final var service = new GitHubWebhookService(config, new GitHubClient(config, new ObjectMapper()),
                new ObjectMapper(), deliveries, changeRequests, sync, fanout, importer);

        assertEquals("processed", service.process(sign(OPENED), "d9", "pull_request", OPENED).status());

        final var captor = ArgumentCaptor.forClass(ChangeRequestSync.class);
        verify(sync).synchronize(captor.capture());
        assertEquals("github:42:8", captor.getValue().changeRequest().id());
        assertEquals(ChangeRequestSync.Action.UPSERT, captor.getValue().action());
        verify(userMergeRequests).upsert(eq("alice-arnold"), any(ChangeRequestRecord.class),
                eq(Set.of(MergeRequestRole.AUTHOR)), anyLong());
        verify(userMergeRequests).upsert(eq("carol-arnold"), any(ChangeRequestRecord.class),
                eq(Set.of(MergeRequestRole.REVIEWER)), anyLong());
        verify(mergeQueue).reconcile("carol-arnold", carolAssociations);
        verify(fanout).synchronize(eq(null), any(ChangeRequestRecord.class));
    }

    private static String sign(final byte[] body) throws Exception {
        final var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
