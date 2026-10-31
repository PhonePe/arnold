package com.phonepe.devx.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.devx.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.devx.arnold.core.model.copilot.CopilotExecutionGrant;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AerospikeCopilotExecutionGrantStore extends AerospikeStore<CopilotExecutionGrant> {
    @Inject
    public AerospikeCopilotExecutionGrantStore(final IAerospikeClient client, final ArnoldCoreConfiguration config,
                                              final ObjectMapper mapper) {
        super(client, new NamespaceSet(config.getAerospikeStorageConfiguration().oauthStateSet().namespace(),
                config.getCopilotConfiguration().getCredentialSet() + "-executions"), mapper,
                CopilotExecutionGrant.class, new DefaultErrorHandler<>(), INDEX_DISABLED_STORE_SETTING);
    }

    @Override
    protected int expiration(final CopilotExecutionGrant grant) {
        return Math.max(1, (int) ((grant.expiresAt() - System.currentTimeMillis()) / 1000));
    }
}
