package com.phonepe.arnold.models.integration;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Set;

@Value
@Builder
@Jacksonized
public class ConnectGitLabRequest {
    @NotNull
    CredentialType credentialType;
    @NotBlank
    String secretReference;
    Long gitlabUserId;
    String gitlabUsername;
    Set<String> scopes;
    Long expiresAt;

    public enum CredentialType {
        PAT,
        OAUTH
    }
}
