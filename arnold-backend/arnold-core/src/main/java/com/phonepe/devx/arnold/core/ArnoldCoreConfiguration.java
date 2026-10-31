package com.phonepe.devx.arnold.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.phonepe.devx.arnold.core.config.AerospikeStorageConfiguration;
import com.phonepe.devx.arnold.core.config.AnalysisConfiguration;
import com.phonepe.devx.arnold.core.config.CopilotConfiguration;
import com.phonepe.devx.arnold.core.config.OpenCodeConfiguration;
import com.phonepe.devx.arnold.core.config.GitLabSyncConfiguration;
import com.phonepe.devx.arnold.core.config.GitLabConfiguration;
import com.phonepe.devx.arnold.core.config.SlackClientConfig;
import com.phonepe.devx.arnold.core.config.UserSyncConfiguration;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArnoldCoreConfiguration {
    private CopilotConfiguration copilotConfiguration = new CopilotConfiguration();
    private OpenCodeConfiguration openCodeConfiguration = new OpenCodeConfiguration();
    private AnalysisConfiguration analysisConfiguration = new AnalysisConfiguration();
    private AsyncExecutorConfig asyncExecutor;
    private AerospikeStorageConfiguration aerospikeStorageConfiguration;
    private UserSyncConfiguration userSyncConfiguration;
    private GitLabSyncConfiguration gitlabSyncConfiguration;
    private GitLabConfiguration gitlabConfiguration;
    private SlackClientConfig slackClientConfig;
}
