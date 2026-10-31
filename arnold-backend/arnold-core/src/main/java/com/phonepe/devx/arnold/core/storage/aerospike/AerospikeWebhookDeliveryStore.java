package com.phonepe.devx.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.devx.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.devx.arnold.core.model.sync.WebhookDelivery;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AerospikeWebhookDeliveryStore extends AerospikeStore<WebhookDelivery> {
    static final int RETENTION_SECONDS = 7 * 24 * 60 * 60;

    @Inject
    public AerospikeWebhookDeliveryStore(final IAerospikeClient client,
                                         final @AerospikeSets.WebhookDelivery NamespaceSet set,
                                         final ObjectMapper mapper) {
        super(client, set, mapper, WebhookDelivery.class, new DefaultErrorHandler<>());
    }

    @Override
    protected int expiration(final WebhookDelivery item) {
        return RETENTION_SECONDS;
    }
}
