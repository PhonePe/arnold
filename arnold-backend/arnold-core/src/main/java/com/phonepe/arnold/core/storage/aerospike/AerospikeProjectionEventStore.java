package com.phonepe.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.arnold.core.model.projection.ProjectionEvent;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AerospikeProjectionEventStore extends AerospikeStore<ProjectionEvent> {
    @Inject
    public AerospikeProjectionEventStore(final IAerospikeClient client,
                                         final @AerospikeSets.ProjectionEvent NamespaceSet set,
                                         final ObjectMapper mapper) {
        super(client, set, mapper, ProjectionEvent.class, new DefaultErrorHandler<>());
    }
}
