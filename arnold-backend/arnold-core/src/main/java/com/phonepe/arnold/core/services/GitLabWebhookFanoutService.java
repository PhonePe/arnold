package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;

import java.util.Set;

public interface GitLabWebhookFanoutService {
    void reviewRequested(ChangeRequestRecord current, Set<Long> reviewerIds);

    void synchronize(ChangeRequestRecord previous, ChangeRequestRecord current);
}
