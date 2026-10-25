package com.phonepe.arnold.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.phonepe.arnold.core.auth.AuthConfiguration;
import com.phonepe.arnold.core.config.AerospikeStorageConfiguration;
import com.phonepe.arnold.core.config.CopilotConfiguration;
import com.phonepe.arnold.core.config.OpenCodeConfiguration;
import com.phonepe.arnold.core.config.GitLabSyncConfiguration;
import com.phonepe.arnold.core.config.GitLabConfiguration;
import com.phonepe.arnold.core.config.SlackClientConfig;
import com.phonepe.arnold.core.config.UserSyncConfiguration;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

import javax.validation.Valid;

@Data
@FieldNameConstants
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArnoldCoreConfiguration {
    @Valid
    private AuthConfiguration auth = new AuthConfiguration();
    private CopilotConfiguration copilotConfiguration = new CopilotConfiguration();
    private OpenCodeConfiguration openCodeConfiguration = new OpenCodeConfiguration();
    private AsyncExecutorConfig asyncExecutor;
    private AerospikeStorageConfiguration aerospikeStorageConfiguration;
    private UserSyncConfiguration userSyncConfiguration;
    private GitLabSyncConfiguration gitlabSyncConfiguration;
    private GitLabConfiguration gitlabConfiguration;
    private SlackClientConfig slackClientConfig;
    @Valid
    private com.phonepe.arnold.core.runner.RunnerConfiguration runner;
}
