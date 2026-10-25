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
import com.phonepe.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.arnold.core.storage.UserChangeRequestStore;
import com.phonepe.arnold.core.storage.VersionedValue;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Singleton
public class AerospikeUserChangeRequestStore extends AerospikeStore<UserChangeRequest>
        implements UserChangeRequestStore {
    @Inject
    public AerospikeUserChangeRequestStore(final IAerospikeClient client,
                                           final @AerospikeSets.UserChangeRequest NamespaceSet set,
                                           final ObjectMapper mapper) {
        super(client, set, mapper, UserChangeRequest.class, new DefaultErrorHandler<>());
    }

    @Override
    public Optional<VersionedValue<UserChangeRequest>> getVersioned(final String id) {
        return getWithVersion(id).map(value -> new VersionedValue<>(value.value(), value.version()));
    }

    @Override
    public void create(final UserChangeRequest association) {
        create(association, List.of(UserChangeRequest.userReference(association.userId()),
                UserChangeRequest.mergeRequestReference(association.mergeRequestId())));
    }

    @Override
    public void update(final UserChangeRequest association, final Object expectedVersion) {
        checkAndUpdate(association, (Long) expectedVersion);
    }

    @Override
    public List<UserChangeRequest> findByUser(final String userId) {
        return getByRefId(UserChangeRequest.userReference(userId));
    }

    @Override
    public List<UserChangeRequest> findByMergeRequest(final String mergeRequestId) {
        return getByRefId(UserChangeRequest.mergeRequestReference(mergeRequestId));
    }
}
