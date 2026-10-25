package com.phonepe.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.arnold.core.model.runner.RunnerJob;
import com.phonepe.arnold.core.storage.RunnerJobRepository;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AerospikeRunnerJobStore extends AerospikeStore<RunnerJob> implements RunnerJobRepository {
    static final int RETENTION_SECONDS = 14 * 24 * 60 * 60;

    @Inject
    public AerospikeRunnerJobStore(final IAerospikeClient client,
                                   final @AerospikeSets.RunnerJob NamespaceSet namespaceSet,
                                   final ObjectMapper mapper) {
        super(client, namespaceSet, mapper, RunnerJob.class, new DefaultErrorHandler<>());
    }

    @Override
    protected int expiration(final RunnerJob item) {
        return RETENTION_SECONDS;
    }
}
