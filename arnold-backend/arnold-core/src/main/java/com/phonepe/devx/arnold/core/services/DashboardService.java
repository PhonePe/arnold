package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.dashboard.DashboardResponse;
import com.phonepe.devx.arnold.core.auth.ArnoldPrincipal;

public interface DashboardService {
    DashboardResponse get(ArnoldPrincipal principal);
}
