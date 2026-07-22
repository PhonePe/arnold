package com.phonepe.devx.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.devx.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.devx.arnold.core.model.analysis.AnalysisRun;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AerospikeAnalysisRunStore extends AerospikeStore<AnalysisRun> {
    @Inject
    public AerospikeAnalysisRunStore(final IAerospikeClient client,
                                     final @AerospikeSets.AnalysisRun NamespaceSet set,
                                     final ObjectMapper mapper) {
        super(client, set, mapper, AnalysisRun.class, new DefaultErrorHandler<>());
    }
}
