package com.phonepe.devx.arnold.core.services.copilot;

public class CopilotClientFailure extends RuntimeException {
    private final int status;
    private final String code;

    public CopilotClientFailure(final int status, final String code) {
        super("GitHub authentication request failed");
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
}
