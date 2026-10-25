package com.phonepe.arnold.core.storage;

import com.phonepe.arnold.core.model.vcs.UserChangeRequest;

import java.util.List;
import java.util.Optional;

public interface UserChangeRequestStore {
    Optional<VersionedValue<UserChangeRequest>> getVersioned(String id);

    void create(UserChangeRequest association);

    void update(UserChangeRequest association, Object expectedVersion);

    List<UserChangeRequest> findByUser(String userId);

    List<UserChangeRequest> findByMergeRequest(String mergeRequestId);
}
