package com.phonepe.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.arnold.core.model.review.ArnoldReview;
import com.phonepe.arnold.core.storage.ReviewStateStore;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class AerospikeReviewStateStore extends AerospikeStore<ArnoldReview> implements ReviewStateStore {

    @Inject
    public AerospikeReviewStateStore(final IAerospikeClient client,
                                     final @AerospikeSets.Review NamespaceSet namespaceSet,
                                     final ObjectMapper mapper) {
        super(client, namespaceSet, mapper, ArnoldReview.class, new DefaultErrorHandler<>());
    }

    @Override
    public Map<String, ArnoldReview> get(final Collection<String> mergeRequestIds) {
        return get(List.copyOf(mergeRequestIds));
    }
}
