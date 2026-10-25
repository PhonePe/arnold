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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.arnold.core.model.mergequeue.MergeRequestGroupCollection;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Read-only view over the legacy per-user group collections; used by the housekeeping migration only.
 */
@Singleton
public class AerospikeLegacyMergeRequestGroupStore extends AerospikeStore<MergeRequestGroupCollection> {
    @Inject
    public AerospikeLegacyMergeRequestGroupStore(final IAerospikeClient client,
                                                 final @AerospikeSets.MergeRequestGroup NamespaceSet set,
                                                 final ObjectMapper mapper) {
        super(client, set, mapper, MergeRequestGroupCollection.class, new DefaultErrorHandler<>(),
                INDEX_DISABLED_STORE_SETTING);
    }
}
