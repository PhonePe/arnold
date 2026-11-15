/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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
package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.provider.github.GitHubIntegration;
import com.phonepe.arnold.core.runner.RunnerBackends;
import com.phonepe.arnold.core.services.copilot.CopilotConnectionService;
import com.phonepe.arnold.models.dashboard.SetupResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * Summarises the deployment's integrations and the user's connections for first-run guidance.
 */
@Slf4j
@Singleton
@AllArgsConstructor(onConstructor_ = @Inject)
public class SetupService {
    private final ArnoldCoreConfiguration configuration;
    private final GitHubIntegration github;
    private final VersionControlIntegrationService integrations;
    private final CopilotConnectionService copilot;

    public SetupResponse setup(final String userId) {
        return new SetupResponse(
                List.of(forge(VersionControlProviderType.GITHUB, github.configured(), userId),
                        forge(VersionControlProviderType.GITLAB, gitLabConfigured(), userId)),
                copilot.status(userId),
                RunnerBackends.effective(configuration).getMode().name());
    }

    public boolean gitLabConfigured() {
        final var gitlab = configuration.getGitlabConfiguration();
        return gitlab != null && notBlank(gitlab.clientId()) && notBlank(gitlab.clientSecret());
    }

    private SetupResponse.Forge forge(final VersionControlProviderType provider, final boolean configured,
                                      final String userId) {
        final var installUrl = provider == VersionControlProviderType.GITHUB && configured
                ? github.installationUrl() : null;
        if (!configured) {
            return new SetupResponse.Forge(provider.name(), false, false, null, null, "unconfigured");
        }
        final var status = integrations.status(provider, userId);
        return new SetupResponse.Forge(provider.name(), true, status.connected(), status.providerUsername(),
                installUrl, status.status());
    }

    private static boolean notBlank(final String value) {
        return value != null && !value.isBlank();
    }
}
