package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.UserChangeRequest;

import java.util.Collection;
import java.util.Set;

public interface ChangeRequestNotificationPublisher {
    void reviewRequested(ChangeRequestRecord changeRequest, Set<String> recipients);

    void rolesChanged(ChangeRequestRecord changeRequest, UserChangeRequest previous, UserChangeRequest current);

    void stateChanged(ChangeRequestRecord previous,
                      ChangeRequestRecord current,
                      Collection<UserChangeRequest> associations);
}
