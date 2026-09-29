package com.phonepe.devx.arnold.core.model.copilot;

import com.livetheoogway.crudstore.core.Id;

public record CopilotAccount(String id, Credential credential, Attempt attempt, String operationId, long busyUntil)
        implements Id {
    public record Credential(String access, String refresh, Long expiresAt, Long refreshExpiresAt,
                             String githubUserId, String username, String status, long checkedAt) { }

    public record Attempt(String id, String deviceCode, String userCode, String verificationUri,
                          long expiresAt, int interval, long nextPollAt, String status) { }

    public CopilotAccount withAttempt(final Attempt value) {
        return new CopilotAccount(id, credential, value, null, 0);
    }

    public CopilotAccount withCredential(final Credential value) {
        return new CopilotAccount(id, value, attempt, null, 0);
    }
}
