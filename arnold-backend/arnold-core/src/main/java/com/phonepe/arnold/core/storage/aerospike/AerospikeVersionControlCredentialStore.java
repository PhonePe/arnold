package com.phonepe.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.arnold.core.model.vcs.VersionControlCredential;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AerospikeVersionControlCredentialStore extends AerospikeStore<VersionControlCredential> {
    @Inject
    public AerospikeVersionControlCredentialStore(final IAerospikeClient client,
                                                  final @AerospikeSets.VersionControlCredential NamespaceSet set,
                                                  final ObjectMapper mapper) {
        super(client, set, mapper, VersionControlCredential.class, new DefaultErrorHandler<>());
    }
}
