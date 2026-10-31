package com.phonepe.devx.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.devx.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.devx.arnold.core.model.vcs.ProviderIdentityLink;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AerospikeProviderIdentityLinkStore extends AerospikeStore<ProviderIdentityLink> {
    @Inject
    public AerospikeProviderIdentityLinkStore(final IAerospikeClient client,
                                              final @AerospikeSets.ProviderIdentityLink NamespaceSet set,
                                              final ObjectMapper mapper) {
        super(client, set, mapper, ProviderIdentityLink.class, new DefaultErrorHandler<>(),
                INDEX_DISABLED_STORE_SETTING);
    }
}
