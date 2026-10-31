package com.phonepe.devx.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.devx.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.devx.arnold.core.model.review.ReviewProgress;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AerospikeReviewProgressStore extends AerospikeStore<ReviewProgress> {
    static final int RETENTION_SECONDS = 90 * 24 * 60 * 60;

    @Inject
    public AerospikeReviewProgressStore(final IAerospikeClient client,
                                        final @AerospikeSets.Review NamespaceSet namespaceSet,
                                        final ObjectMapper mapper) {
        super(client, namespaceSet, mapper, ReviewProgress.class, new DefaultErrorHandler<>());
    }

    @Override
    protected int expiration(final ReviewProgress item) {
        return RETENTION_SECONDS;
    }
}
