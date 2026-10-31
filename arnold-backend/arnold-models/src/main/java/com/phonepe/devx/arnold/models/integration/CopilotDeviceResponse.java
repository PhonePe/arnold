package com.phonepe.devx.arnold.models.integration;

public record CopilotDeviceResponse(String attemptId, String userCode, String verificationUri,
                                    long expiresAt, int interval, String status,
                                    CopilotConnectionResponse connection) { }
