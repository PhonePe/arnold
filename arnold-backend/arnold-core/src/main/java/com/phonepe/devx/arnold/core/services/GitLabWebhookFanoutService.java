package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;

public interface GitLabWebhookFanoutService {
    void synchronize(ChangeRequestRecord previous, ChangeRequestRecord current);
}
