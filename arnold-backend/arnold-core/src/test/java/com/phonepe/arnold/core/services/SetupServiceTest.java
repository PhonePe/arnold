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
package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.config.GitLabConfiguration;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.provider.github.GitHubIntegration;
import com.phonepe.arnold.core.runner.RunnerConfiguration;
import com.phonepe.arnold.core.runner.RunnerMode;
import com.phonepe.arnold.core.services.copilot.CopilotConnectionService;
import com.phonepe.arnold.models.integration.CopilotConnectionResponse;
import com.phonepe.arnold.models.integration.VersionControlIntegrationResponse;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetupServiceTest {
    private final GitHubIntegration github = mock(GitHubIntegration.class);
    private final VersionControlIntegrationService integrations = mock(VersionControlIntegrationService.class);
    private final CopilotConnectionService copilot = mock(CopilotConnectionService.class);
    private final ArnoldCoreConfiguration configuration = new ArnoldCoreConfiguration();

    @Test
    void testSetup_shouldReportConnectedGitHubAndUnconfiguredGitLab_whenOnlyGitHubIsSetUp() {
        configuration.setGitlabConfiguration(new GitLabConfiguration("https://gitlab.com", "", "",
                "http://localhost:3000/gitlab/oauth/callback", 1, 1, 1));
        final var runner = new RunnerConfiguration();
        runner.setMode(RunnerMode.LOCAL);
        configuration.setRunner(runner);
        when(github.configured()).thenReturn(true);
        when(github.installationUrl()).thenReturn("https://github.com/apps/arnold/installations/new");
        when(integrations.status(VersionControlProviderType.GITHUB, "u1")).thenReturn(new VersionControlIntegrationResponse(
                true, "GITHUB", "GITHUB_APP", "1", "octocat", Set.of(), null, "connected"));
        final var copilotStatus = new CopilotConnectionResponse(false, false, null, "disabled", null, null);
        when(copilot.status("u1")).thenReturn(copilotStatus);

        final var setup = new SetupService(configuration, github, integrations, copilot).setup("u1");

        final var gitHub = setup.forges().get(0);
        assertEquals("GITHUB", gitHub.provider());
        assertTrue(gitHub.configured() && gitHub.connected());
        assertEquals("octocat", gitHub.username());
        assertEquals("https://github.com/apps/arnold/installations/new", gitHub.installUrl());
        assertEquals("connected", gitHub.status());
        final var gitLab = setup.forges().get(1);
        assertFalse(gitLab.configured() || gitLab.connected());
        assertNull(gitLab.installUrl());
        verify(integrations, never()).status(org.mockito.ArgumentMatchers.eq(VersionControlProviderType.GITLAB), any());
        assertEquals(copilotStatus, setup.copilot());
        assertEquals("LOCAL", setup.runnerMode());
    }

    @Test
    void testSetup_shouldReportReconnectRequired_whenStoredCredentialWasRejected() {
        final var store = mock(com.phonepe.arnold.core.storage.aerospike.AerospikeVersionControlCredentialStore.class);
        when(store.get("github:u1")).thenReturn(java.util.Optional.of(
                new com.phonepe.arnold.core.model.vcs.VersionControlCredential("github:u1", "u1",
                        VersionControlProviderType.GITHUB, "GITHUB_APP", "ghu", "ghr", "1", "octocat", Set.of(),
                        null, "reconnect_required", 1L, 1L)));
        when(github.configured()).thenReturn(true);
        when(copilot.status("u1")).thenReturn(new CopilotConnectionResponse(false, false, null, "disabled", null,
                null));

        final var setup = new SetupService(configuration, github,
                new com.phonepe.arnold.core.services.impl.VersionControlIntegrationServiceImpl(store), copilot)
                .setup("u1");

        final var gitHub = setup.forges().get(0);
        assertFalse(gitHub.connected());
        assertEquals("reconnect_required", gitHub.status());
        assertEquals("octocat", gitHub.username());
    }
}
