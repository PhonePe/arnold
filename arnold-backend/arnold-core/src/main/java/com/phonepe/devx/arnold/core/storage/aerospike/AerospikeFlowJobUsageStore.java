package com.phonepe.devx.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.phonepe.devx.arnold.core.config.AerospikeStorageConfiguration;
import com.phonepe.devx.arnold.core.model.analysis.FlowJobUsage;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AerospikeFlowJobUsageStore extends AerospikeStore<FlowJobUsage> {
    static final int RETENTION_SECONDS = 90 * 24 * 60 * 60;

    @Inject
    public AerospikeFlowJobUsageStore(final IAerospikeClient client,
                                      final @Config AerospikeStorageConfiguration configuration,
                                      final ObjectMapper mapper) {
        super(client, configuration.flowJobUsageSet(), mapper, FlowJobUsage.class, new DefaultErrorHandler<>());
    }

    @Override
    protected int expiration(final FlowJobUsage item) {
        return RETENTION_SECONDS;
    }
}
