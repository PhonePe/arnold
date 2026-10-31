package com.phonepe.devx.arnold.core.model.sync;

import com.livetheoogway.crudstore.core.Id;

public record WebhookDelivery(String id, String eventType, String status, long receivedAt, long processedAt) implements Id {
}
