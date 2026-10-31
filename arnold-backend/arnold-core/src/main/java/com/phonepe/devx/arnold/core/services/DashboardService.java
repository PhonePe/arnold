package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.dashboard.DashboardResponse;
import com.phonepe.olympus.im.models.user.UserDetails;

public interface DashboardService {
    DashboardResponse get(UserDetails userDetails);
}
