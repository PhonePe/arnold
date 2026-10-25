package com.phonepe.arnold.core.config;

import lombok.Data;
import lombok.ToString;

/**
 * Opt-in GitHub Copilot harness. Disabled by default.
 *
 * <p>Copilot token exchange relies on GitHub endpoints that are not part of GitHub's documented public API and
 * may change without notice. Operators must supply their own GitHub OAuth app {@code clientId} and confirm that
 * this use is permitted under their GitHub agreement before enabling it.</p>
 */
@Data
public class CopilotConfiguration {
    private boolean enabled;
    private boolean jobTokenExchangeEnabled;
    private String clientId;
    private String credentialSet = "arnold-copilot-v1";
    @ToString.Exclude
    private String encryptionKey;
}
