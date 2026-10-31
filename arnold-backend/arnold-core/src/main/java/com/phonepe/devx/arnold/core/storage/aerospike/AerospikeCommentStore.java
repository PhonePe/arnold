package com.phonepe.devx.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.devx.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.devx.arnold.core.model.comment.CommentRecord;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AerospikeCommentStore extends AerospikeStore<CommentRecord> {
    @Inject
    public AerospikeCommentStore(final IAerospikeClient client,
                                 final @AerospikeSets.Comment NamespaceSet set,
                                 final ObjectMapper mapper) {
        super(client, set, mapper, CommentRecord.class, new DefaultErrorHandler<>());
    }
}
