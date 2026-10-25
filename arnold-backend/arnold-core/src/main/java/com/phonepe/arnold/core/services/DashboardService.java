package com.phonepe.arnold.core.services;

import com.phonepe.arnold.models.dashboard.DashboardResponse;
import com.phonepe.arnold.core.auth.ArnoldPrincipal;

public interface DashboardService {
    DashboardResponse get(ArnoldPrincipal principal);
}
