package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;

public interface ChangeRequestService {
    MergeRequestResponse get(long projectId, long iid, String userId);

    MergeRequestResponse resolve(String url, String userId);

    boolean synchronize(ChangeRequestRecord mergeRequest);
}
