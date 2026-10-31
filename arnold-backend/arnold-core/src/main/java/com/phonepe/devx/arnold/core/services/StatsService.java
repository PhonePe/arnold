package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.stats.StatsResponse;

public interface StatsService {
    StatsResponse personal(String userId);

    StatsResponse organization();
}
