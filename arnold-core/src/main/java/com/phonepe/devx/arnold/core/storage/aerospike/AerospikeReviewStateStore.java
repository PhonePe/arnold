package com.phonepe.devx.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.devx.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.devx.arnold.core.model.review.ArnoldReview;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class AerospikeReviewStateStore extends AerospikeStore<ArnoldReview> {

    @Inject
    public AerospikeReviewStateStore(final IAerospikeClient client,
                                     final @AerospikeSets.Review NamespaceSet namespaceSet,
                                     final ObjectMapper mapper) {
        super(client, namespaceSet, mapper, ArnoldReview.class, new DefaultErrorHandler<>());
    }

}
