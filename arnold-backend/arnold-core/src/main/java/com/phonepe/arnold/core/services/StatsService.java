package com.phonepe.arnold.core.services;

import com.phonepe.arnold.models.stats.StatsResponse;

public interface StatsService {
    StatsResponse personal(String userId);

    StatsResponse organization();
}
