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

import com.aerospike.client.IAerospikeClient;
import com.aerospike.client.Record;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.livetheoogway.crudstore.aerospike.RecordDetails;
import com.phonepe.arnold.core.crypto.VersionControlCredentialCipher;
import com.phonepe.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.arnold.core.model.vcs.VersionControlCredential;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 * Stores version control credentials with access and refresh tokens encrypted. Every write path goes through
 * {@link #recordDetails} and every read path through {@link #extractItemFromRecord}.
 */
@Singleton
public class AerospikeVersionControlCredentialStore extends AerospikeStore<VersionControlCredential> {
    private final VersionControlCredentialCipher cipher;

    @Inject
    public AerospikeVersionControlCredentialStore(final IAerospikeClient client,
                                                  final @AerospikeSets.VersionControlCredential NamespaceSet set,
                                                  final ObjectMapper mapper,
                                                  final VersionControlCredentialCipher cipher) {
        super(client, set, mapper, VersionControlCredential.class, new DefaultErrorHandler<>());
        this.cipher = cipher;
    }

    @Override
    protected RecordDetails recordDetails(final VersionControlCredential item, final List<String> refIds)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        return super.recordDetails(cipher.encrypt(item), refIds);
    }

    @Override
    protected Optional<VersionControlCredential> extractItemFromRecord(final String id, final Record record) {
        return super.extractItemFromRecord(id, record).map(cipher::decrypt);
    }
}
