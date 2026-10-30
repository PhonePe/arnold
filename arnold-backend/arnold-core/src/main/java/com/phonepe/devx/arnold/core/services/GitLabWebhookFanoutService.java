package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;

import java.util.Set;

public interface GitLabWebhookFanoutService {
    void reviewRequested(ChangeRequestRecord current, Set<Long> reviewerIds);

    void synchronize(ChangeRequestRecord previous, ChangeRequestRecord current);
}
