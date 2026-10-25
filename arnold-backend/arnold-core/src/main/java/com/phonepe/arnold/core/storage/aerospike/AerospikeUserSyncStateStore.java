package com.phonepe.arnold.core.storage.aerospike;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.IAerospikeClient;
import com.aerospike.client.query.Filter;
import com.aerospike.client.query.IndexType;
import com.aerospike.client.query.Statement;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.livetheoogway.crudstore.aerospike.RecordDetails;
import com.phonepe.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.arnold.core.model.sync.UserSyncState;
import com.phonepe.arnold.core.storage.UserSyncStateStore;
import com.phonepe.arnold.core.storage.VersionedValue;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Singleton
public class AerospikeUserSyncStateStore extends AerospikeStore<UserSyncState> implements UserSyncStateStore {
    private static final String NEXT_DUE_AT_BIN = "nextDueAt";
    private static final String NEXT_DUE_AT_INDEX = "arnold_user_sync_next_due_idx";

    @Inject
    public AerospikeUserSyncStateStore(final IAerospikeClient client,
                                       final @AerospikeSets.UserSyncState NamespaceSet set,
                                       final ObjectMapper mapper) {
        super(client, set, mapper, UserSyncState.class, new DefaultErrorHandler<>(), INDEX_DISABLED_STORE_SETTING);
        setupDueIndex();
    }

    @Override
    protected RecordDetails recordDetails(final UserSyncState item, final List<String> ignored)
            throws JsonProcessingException {
        return new RecordDetails(-1, new Bin(storeSetting.dataBin(), mapper.writeValueAsString(item)),
                new Bin(NEXT_DUE_AT_BIN, item.nextDueAt()));
    }

    @Override
    public Optional<VersionedValue<UserSyncState>> getVersioned(final String id) {
        return getWithVersion(id).map(value -> new VersionedValue<>(value.value(), value.version()));
    }

    @Override
    public void update(final UserSyncState state, final Object expectedVersion) {
        checkAndUpdate(state, (Long) expectedVersion);
    }

    @Override
    public List<UserSyncState> due(final long now, final int limit) {
        final var statement = new Statement();
        statement.setNamespace(namespaceSet.namespace());
        statement.setSetName(namespaceSet.set());
        statement.setIndexName(NEXT_DUE_AT_INDEX);
        statement.setBinNames(storeSetting.dataBin());
        statement.setFilter(Filter.range(NEXT_DUE_AT_BIN, 0, now));
        final var results = new ArrayList<UserSyncState>();
        try (final var records = client.query(client.getQueryPolicyDefault(), statement)) {
            while (records.next() && results.size() < limit) {
                try {
                    results.add(mapper.readValue(records.getRecord().getString(storeSetting.dataBin()),
                            UserSyncState.class));
                } catch (JsonProcessingException malformedRecord) {
                    throw new IllegalStateException("Failed to read a due user sync state", malformedRecord);
                }
            }
        }
        return results.stream().sorted(Comparator.comparingLong(UserSyncState::nextDueAt)).toList();
    }

    private void setupDueIndex() {
        try {
            final var task = client.createIndex(null, namespaceSet.namespace(), namespaceSet.set(),
                    NEXT_DUE_AT_INDEX, NEXT_DUE_AT_BIN, IndexType.NUMERIC);
            if (task != null) {
                task.waitTillComplete();
            }
        } catch (AerospikeException e) {
            if (e.getResultCode() != 100 && e.getResultCode() != 200) {
                throw e;
            }
        }
    }
}
