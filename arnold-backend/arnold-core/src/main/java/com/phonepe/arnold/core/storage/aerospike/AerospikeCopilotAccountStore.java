package com.phonepe.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.model.copilot.CopilotAccount;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AerospikeCopilotAccountStore extends AerospikeStore<CopilotAccount> {
    @Inject
    public AerospikeCopilotAccountStore(final IAerospikeClient client, final ArnoldCoreConfiguration config,
                                        final ObjectMapper mapper) {
        super(client, new NamespaceSet(config.getAerospikeStorageConfiguration().oauthStateSet().namespace(),
                config.getCopilotConfiguration().getCredentialSet()), mapper, CopilotAccount.class,
                new DefaultErrorHandler<>());
    }
}
