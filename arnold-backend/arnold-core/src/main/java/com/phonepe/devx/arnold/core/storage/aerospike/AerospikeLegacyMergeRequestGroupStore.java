package com.phonepe.devx.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.devx.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.devx.arnold.core.model.mergequeue.MergeRequestGroupCollection;

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
