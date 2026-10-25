package com.phonepe.arnold.core.services;

import com.phonepe.arnold.models.callback.CallbackResponse;
import com.phonepe.arnold.models.callback.GitLabWebhookRequest;

public interface GitLabCallbackService {
    CallbackResponse process(String token, String deliveryId, String eventType, GitLabWebhookRequest request);
}
