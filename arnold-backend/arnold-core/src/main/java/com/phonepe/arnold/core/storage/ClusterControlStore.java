package com.phonepe.arnold.core.storage;

import com.phonepe.arnold.core.model.cluster.ClusterControlRecord;

import java.util.Optional;

public interface ClusterControlStore {
    Optional<ClusterControlRecord> get(String id);

    Optional<VersionedValue<ClusterControlRecord>> getVersioned(String id);

    void create(ClusterControlRecord record);

    void update(ClusterControlRecord record, Object expectedVersion);
}
