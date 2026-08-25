package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.UserChangeRequest;

import java.util.Collection;

public interface ChangeRequestNotificationPublisher {
    void rolesChanged(ChangeRequestRecord changeRequest, UserChangeRequest previous, UserChangeRequest current);

    void stateChanged(ChangeRequestRecord previous,
                      ChangeRequestRecord current,
                      Collection<UserChangeRequest> associations);
}
