package com.phonepe.devx.arnold.models.integration;

public record CopilotConnectionResponse(boolean enabled, boolean connected, String username,
                                        String status, Long checkedAt, Long expiresAt) { }
