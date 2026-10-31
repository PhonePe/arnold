package com.phonepe.devx.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.devx.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.devx.arnold.core.model.notification.UserNotificationInbox;
import com.phonepe.devx.arnold.core.storage.NotificationStore;
import com.phonepe.devx.arnold.core.storage.VersionedValue;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class AerospikeNotificationStore extends AerospikeStore<UserNotificationInbox> implements NotificationStore {
    private static final int RETENTION_SECONDS = 30 * 24 * 60 * 60;

    @Inject
    public AerospikeNotificationStore(final IAerospikeClient client,
                                      final @AerospikeSets.Notification NamespaceSet set,
                                      final ObjectMapper mapper) {
        super(client, set, mapper, UserNotificationInbox.class, new DefaultErrorHandler<>(),
                INDEX_DISABLED_STORE_SETTING);
    }

    @Override
    protected int expiration(final UserNotificationInbox item) {
        return RETENTION_SECONDS;
    }

    @Override
    public Optional<VersionedValue<UserNotificationInbox>> getVersioned(final String id) {
        return getWithVersion(id).map(value -> new VersionedValue<>(value.value(), value.version()));
    }

    @Override
    public void create(final UserNotificationInbox inbox) {
        super.create(inbox);
    }

    @Override
    public void update(final UserNotificationInbox inbox, final Object expectedVersion) {
        checkAndUpdate(inbox, (Long) expectedVersion);
    }
}
