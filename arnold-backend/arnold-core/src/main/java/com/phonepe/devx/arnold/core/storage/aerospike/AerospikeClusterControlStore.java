package com.phonepe.devx.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.devx.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.devx.arnold.core.model.cluster.ClusterControlRecord;
import com.phonepe.devx.arnold.core.storage.ClusterControlStore;
import com.phonepe.devx.arnold.core.storage.VersionedValue;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class AerospikeClusterControlStore extends AerospikeStore<ClusterControlRecord>
        implements ClusterControlStore {
    @Inject
    public AerospikeClusterControlStore(final IAerospikeClient client,
                                        final @AerospikeSets.ClusterControl NamespaceSet set,
                                        final ObjectMapper mapper) {
        super(client, set, mapper, ClusterControlRecord.class, new DefaultErrorHandler<>(),
                INDEX_DISABLED_STORE_SETTING);
    }

    @Override
    public Optional<VersionedValue<ClusterControlRecord>> getVersioned(final String id) {
        return getWithVersion(id).map(value -> new VersionedValue<>(value.value(), value.version()));
    }

    @Override
    public void update(final ClusterControlRecord record, final Object expectedVersion) {
        checkAndUpdate(record, (Long) expectedVersion);
    }
}
