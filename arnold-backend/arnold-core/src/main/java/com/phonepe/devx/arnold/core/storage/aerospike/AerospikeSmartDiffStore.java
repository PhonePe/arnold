package com.phonepe.devx.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.devx.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.devx.arnold.core.model.review.SmartDiffRecord;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AerospikeSmartDiffStore extends AerospikeStore<SmartDiffRecord> {
    @Inject
    public AerospikeSmartDiffStore(final IAerospikeClient client,
                                   final @AerospikeSets.Review NamespaceSet namespaceSet,
                                   final ObjectMapper mapper) {
        super(client, namespaceSet, mapper, SmartDiffRecord.class, new DefaultErrorHandler<>());
    }
}
