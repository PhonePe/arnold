package com.phonepe.devx.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.devx.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.devx.arnold.core.model.notification.UserNotification;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class AerospikeNotificationStore extends AerospikeStore<UserNotification> {
    private static final int RETENTION_SECONDS = 30 * 24 * 60 * 60;
    private static final long RETENTION_MILLIS = RETENTION_SECONDS * 1000L;

    @Inject
    public AerospikeNotificationStore(final IAerospikeClient client,
                                      final @AerospikeSets.Notification NamespaceSet set,
                                      final ObjectMapper mapper) {
        super(client, set, mapper, UserNotification.class, new DefaultErrorHandler<>());
    }

    @Override
    protected int expiration(final UserNotification item) {
        final var remaining = item.createdAt() + RETENTION_MILLIS - System.currentTimeMillis();
        return (int) Math.max(1, Math.min(RETENTION_SECONDS, remaining / 1000));
    }

    public void createForUser(final UserNotification notification) {
        create(notification, List.of(UserNotification.userReference(notification.userId())));
    }
}
