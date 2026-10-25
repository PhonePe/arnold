package com.phonepe.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.arnold.core.model.vcs.OAuthState;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AerospikeOAuthStateStore extends AerospikeStore<OAuthState> {
    @Inject
    public AerospikeOAuthStateStore(final IAerospikeClient client,
                                    final @AerospikeSets.OAuthState NamespaceSet set,
                                    final ObjectMapper mapper) {
        super(client, set, mapper, OAuthState.class, new DefaultErrorHandler<>());
    }
}
