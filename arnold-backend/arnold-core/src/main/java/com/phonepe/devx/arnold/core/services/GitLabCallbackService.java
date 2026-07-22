package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.callback.CallbackResponse;
import com.phonepe.devx.arnold.models.callback.GitLabWebhookRequest;

public interface GitLabCallbackService {
    CallbackResponse process(String token, String deliveryId, String eventType, GitLabWebhookRequest request);
}
