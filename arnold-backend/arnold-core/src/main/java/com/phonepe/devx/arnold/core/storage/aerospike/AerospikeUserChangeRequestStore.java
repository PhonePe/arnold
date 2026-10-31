package com.phonepe.devx.arnold.core.storage.aerospike;

import com.aerospike.client.IAerospikeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.aerospike.AerospikeStore;
import com.livetheoogway.crudstore.aerospike.DefaultErrorHandler;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.devx.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.devx.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.devx.arnold.core.storage.UserChangeRequestStore;
import com.phonepe.devx.arnold.core.storage.VersionedValue;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Singleton
public class AerospikeUserChangeRequestStore extends AerospikeStore<UserChangeRequest>
        implements UserChangeRequestStore {
    @Inject
    public AerospikeUserChangeRequestStore(final IAerospikeClient client,
                                           final @AerospikeSets.UserChangeRequest NamespaceSet set,
                                           final ObjectMapper mapper) {
        super(client, set, mapper, UserChangeRequest.class, new DefaultErrorHandler<>());
    }

    @Override
    public Optional<VersionedValue<UserChangeRequest>> getVersioned(final String id) {
        return getWithVersion(id).map(value -> new VersionedValue<>(value.value(), value.version()));
    }

    @Override
    public void create(final UserChangeRequest association) {
        create(association, List.of(UserChangeRequest.userReference(association.userId()),
                UserChangeRequest.mergeRequestReference(association.mergeRequestId())));
    }

    @Override
    public void update(final UserChangeRequest association, final Object expectedVersion) {
        checkAndUpdate(association, (Long) expectedVersion);
    }

    @Override
    public List<UserChangeRequest> findByUser(final String userId) {
        return getByRefId(UserChangeRequest.userReference(userId));
    }

    @Override
    public List<UserChangeRequest> findByMergeRequest(final String mergeRequestId) {
        return getByRefId(UserChangeRequest.mergeRequestReference(mergeRequestId));
    }
}
