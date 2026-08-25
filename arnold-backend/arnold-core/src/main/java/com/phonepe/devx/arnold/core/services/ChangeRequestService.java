package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ChangeRequestService {
    MergeRequestResponse get(VersionControlProviderType provider, String repositoryId, long number, String userId);

    MergeRequestResponse resolve(VersionControlProviderType provider, String url, String userId);

    boolean synchronize(ChangeRequestRecord mergeRequest);

    Optional<ChangeRequestRecord> record(String mergeRequestId);

    Map<String, ChangeRequestRecord> records(Collection<String> mergeRequestIds);

    MergeRequestResponse response(ChangeRequestRecord mergeRequest);
}
