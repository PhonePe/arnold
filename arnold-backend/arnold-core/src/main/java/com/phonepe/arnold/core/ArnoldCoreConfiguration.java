/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.phonepe.arnold.core.auth.AuthConfiguration;
import com.phonepe.arnold.core.config.AerospikeStorageConfiguration;
import com.phonepe.arnold.core.config.AnalysisConfiguration;
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
    private AnalysisConfiguration analysisConfiguration = new AnalysisConfiguration();
    private AsyncExecutorConfig asyncExecutor;
    private AerospikeStorageConfiguration aerospikeStorageConfiguration;
    private UserSyncConfiguration userSyncConfiguration;
    private GitLabSyncConfiguration gitlabSyncConfiguration;
    private GitLabConfiguration gitlabConfiguration;
    @Valid
    private com.phonepe.arnold.core.config.GitHubConfiguration githubConfiguration;
    private SlackClientConfig slackClientConfig;
    @Valid
    private com.phonepe.arnold.core.runner.RunnerConfiguration runner;
}
