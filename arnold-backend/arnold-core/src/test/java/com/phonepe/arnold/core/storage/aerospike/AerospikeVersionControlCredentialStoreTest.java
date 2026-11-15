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
package com.phonepe.arnold.core.storage.aerospike;

import com.aerospike.client.Bin;
import com.aerospike.client.IAerospikeClient;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.QueryPolicy;
import com.aerospike.client.policy.ScanPolicy;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.client.task.IndexTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.arnold.core.crypto.VersionControlCredentialCipher;
import com.phonepe.arnold.core.model.vcs.VersionControlCredential;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AerospikeVersionControlCredentialStoreTest {

    @Test
    void testStore_shouldPersistOnlyCiphertextAndReadPlaintext_whenRoundTripped() {
        final var client = mock(IAerospikeClient.class);
        when(client.getWritePolicyDefault()).thenReturn(new WritePolicy());
        when(client.getReadPolicyDefault()).thenReturn(new Policy());
        when(client.getQueryPolicyDefault()).thenReturn(new QueryPolicy());
        when(client.getScanPolicyDefault()).thenReturn(new ScanPolicy());
        when(client.createIndex(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mock(IndexTask.class));
        final var cipher = new VersionControlCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        final var store = new AerospikeVersionControlCredentialStore(
                client, new NamespaceSet("arnold", "vcs"), new ObjectMapper(), cipher);
        final var credential = new VersionControlCredential("gitlab:alice", "alice", VersionControlProviderType.GITLAB,
                "oauth", "glpat-secret", "refresh-secret", "1", "alice", Set.of("api"), 1L, "ACTIVE", 1L, 2L);

        store.create(credential);

        final var bins = ArgumentCaptor.forClass(Bin[].class);
        verify(client).put(any(WritePolicy.class), any(Key.class), bins.capture());
        final var stored = Arrays.toString(bins.getValue());
        assertFalse(stored.contains("glpat-secret"));
        assertFalse(stored.contains("refresh-secret"));

        final var record = new HashMap<String, Object>();
        Arrays.stream(bins.getValue()).forEach(bin -> record.put(bin.name, bin.value.getObject()));
        when(client.get(any(Policy.class), eq(new Key("arnold", "vcs", "gitlab:alice"))))
                .thenReturn(new Record(record, 1, 0));
        final var read = store.get("gitlab:alice").orElseThrow();
        assertEquals("glpat-secret", read.accessToken());
        assertEquals("refresh-secret", read.refreshToken());
    }
}
