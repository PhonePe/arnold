package com.phonepe.devx.arnold.core.config;

import lombok.Data;
import lombok.ToString;

@Data
public class CopilotConfiguration {
    private boolean enabled;
    private boolean jobTokenExchangeEnabled;
    private String clientId;
    private String credentialSet = "arnold-copilot-v1";
    @ToString.Exclude
    private String encryptionKey;
}
