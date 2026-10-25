package com.phonepe.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.phonepe.arnold.core.config.AerospikeStorageConfiguration;
import com.phonepe.arnold.core.model.analysis.GroupAnalysisRun;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AerospikeGroupAnalysisRunStore extends AerospikeStore<GroupAnalysisRun> {
    @Inject
    public AerospikeGroupAnalysisRunStore(final IAerospikeClient client,
                                          final @Config AerospikeStorageConfiguration configuration,
                                          final ObjectMapper mapper) {
        super(client, configuration.groupAnalysisRunSet(), mapper, GroupAnalysisRun.class, new DefaultErrorHandler<>());
    }
}
