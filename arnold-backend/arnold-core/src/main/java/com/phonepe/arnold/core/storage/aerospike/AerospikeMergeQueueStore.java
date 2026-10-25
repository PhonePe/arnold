package com.phonepe.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.arnold.core.model.mergequeue.MergeQueue;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AerospikeMergeQueueStore extends AerospikeStore<MergeQueue> {

    @Inject
    public AerospikeMergeQueueStore(final IAerospikeClient client,
                                    final @AerospikeSets.MergeQueue NamespaceSet namespaceSet,
                                    final ObjectMapper mapper) {
        super(client, namespaceSet, mapper, MergeQueue.class, new DefaultErrorHandler<>());
    }
}
