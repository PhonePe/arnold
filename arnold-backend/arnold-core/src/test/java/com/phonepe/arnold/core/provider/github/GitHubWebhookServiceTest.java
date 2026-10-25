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
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.sync.WebhookDelivery;
import com.phonepe.arnold.core.model.vcs.ChangeRequestSync;
import com.phonepe.arnold.core.services.ChangeRequestService;
import com.phonepe.arnold.core.services.GitLabWebhookFanoutService;
import com.phonepe.arnold.core.services.VersionControlSyncService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeWebhookDeliveryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubWebhookServiceTest {

    private static final byte[] BODY = """
            {"action":"closed","pull_request":{"id":555,"number":7,"title":"Add cache","state":"closed",
             "merged_at":"2026-01-03T00:00:00Z","html_url":"https://github.com/acme/api/pull/7",
             "created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-03T00:00:00Z",
             "user":{"id":1,"login":"alice"},"assignees":[],"requested_reviewers":[],"labels":[],
             "head":{"ref":"feature","sha":"head"},"base":{"ref":"main","sha":"base",
             "repo":{"id":42,"full_name":"acme/api"}}}}""".getBytes(StandardCharsets.UTF_8);

    private AerospikeWebhookDeliveryStore deliveries;
    private ChangeRequestService changeRequests;
    private VersionControlSyncService sync;
    private GitHubWebhookService service;

    @BeforeEach
    void setUp() {
        final var config = new GitHubConfiguration();
        config.setWebhookSecret("webhook-secret");
        deliveries = mock(AerospikeWebhookDeliveryStore.class);
        changeRequests = mock(ChangeRequestService.class);
        sync = mock(VersionControlSyncService.class);
        service = new GitHubWebhookService(config, new GitHubClient(config, new ObjectMapper()), new ObjectMapper(),
                deliveries, changeRequests, sync, mock(GitLabWebhookFanoutService.class));
    }

    private static String sign(final byte[] body) throws Exception {
        final var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    @Test
    void rejectsMissingOrInvalidSignatures() {
        final var invalid = assertThrows(ArnoldError.class, () -> service.process("sha256=00", "d1", "pull_request", BODY));
        assertEquals(ArnoldErrorCode.AUTH_ERROR, invalid.getErrorCode());
        assertThrows(ArnoldError.class, () -> service.process(null, "d1", "pull_request", BODY));
        verify(sync, never()).synchronize(any(ChangeRequestSync.class));
    }

    @Test
    void synchronizesMergedPullRequest() throws Exception {
        when(deliveries.getWithVersion("github:d1")).thenReturn(Optional.empty(),
                Optional.of(Versioned.of(new WebhookDelivery("github:d1", "pull_request", "processing", 1, 0), 1L)));
        when(changeRequests.record("github:42:7")).thenReturn(Optional.empty());

        final var response = service.process(sign(BODY), "d1", "pull_request", BODY);

        assertEquals("processed", response.status());
        final var captor = ArgumentCaptor.forClass(ChangeRequestSync.class);
        verify(sync).synchronize(captor.capture());
        assertEquals(ChangeRequestSync.Action.MERGED, captor.getValue().action());
        assertEquals("github:42:7", captor.getValue().changeRequest().id());
        assertEquals("head", captor.getValue().changeRequest().headSha());
    }

    @Test
    void skipsDuplicateDeliveriesAndIgnoresOtherEvents() throws Exception {
        when(deliveries.getWithVersion("github:d1")).thenReturn(Optional.of(Versioned.of(
                new WebhookDelivery("github:d1", "pull_request", "processed", 1, 2), 3L)));

        assertEquals("duplicate", service.process(sign(BODY), "d1", "pull_request", BODY).status());
        assertEquals("ignored", service.process(sign(BODY), "d2", "push", BODY).status());
        verify(sync, never()).synchronize(any(ChangeRequestSync.class));
    }
}
