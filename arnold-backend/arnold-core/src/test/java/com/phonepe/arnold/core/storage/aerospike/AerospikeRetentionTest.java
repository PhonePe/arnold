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

package com.phonepe.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.QueryPolicy;
import com.aerospike.client.policy.ScanPolicy;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.client.task.IndexTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AerospikeRetentionTest {
    private static final int DAY_SECONDS = 24 * 60 * 60;
    private final IAerospikeClient client = mock(IAerospikeClient.class);
    private final NamespaceSet set = new NamespaceSet("arnold", "test");
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(client.getWritePolicyDefault()).thenReturn(new WritePolicy());
        when(client.getReadPolicyDefault()).thenReturn(new Policy());
        when(client.getQueryPolicyDefault()).thenReturn(new QueryPolicy());
        when(client.getScanPolicyDefault()).thenReturn(new ScanPolicy());
        when(client.createIndex(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mock(IndexTask.class));
    }

    @Test
    void testExpiration_shouldRetainWebhookDeliveriesForSevenDays_whenWritten() {
        assertEquals(7 * DAY_SECONDS, new AerospikeWebhookDeliveryStore(client, set, mapper).expiration(null));
    }

    @Test
    void testExpiration_shouldRetainAnalysisArtifactsForNinetyDays_whenWritten() {
        assertEquals(90 * DAY_SECONDS, new AerospikeAnalysisRunStore(client, set, mapper).expiration(null));
        assertEquals(90 * DAY_SECONDS, new AerospikeSmartDiffStore(client, set, mapper).expiration(null));
        assertEquals(90 * DAY_SECONDS, new AerospikeReviewProgressStore(client, set, mapper).expiration(null));
    }
}
