package com.phonepe.devx.arnold.core.model.projection;

import com.livetheoogway.crudstore.core.Id;

public record ProjectionEvent(
        String id,
        String aggregateType,
        String aggregateId,
        String operation,
        String status,
        int attempts,
        long createdAt,
        long processedAt
) implements Id {
}
