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

package com.phonepe.arnold.core.runner;

import com.phonepe.arnold.core.provider.github.GitHubIntegration;
import com.phonepe.arnold.core.services.RunnerDispatcher;
import com.phonepe.arnold.core.services.copilot.CopilotExecutionService;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Adds execution credentials to a job and submits it to the configured runner mode.
 */
@Slf4j
@Singleton
public class RunnerDispatcherImpl implements RunnerDispatcher {

    private final RunnerBackends backends;
    static final String SCM_GITHUB_TOKEN = "SCM_GITHUB_TOKEN";

    private final CopilotExecutionService copilot;
    private final GitHubIntegration github;

    @Inject
    public RunnerDispatcherImpl(final RunnerBackends backends, final CopilotExecutionService copilot,
                                final GitHubIntegration github) {
        this.backends = backends;
        this.copilot = copilot;
        this.github = github;
    }

    public RunnerDispatcherImpl(final RunnerBackends backends, final CopilotExecutionService copilot) {
        this(backends, copilot, null);
    }

    @Override
    public long submit(final Map<String, String> original) {
        final var variables = new HashMap<>(copilot.pipelineVariables(original));
        addGitHubToken(variables);
        try {
            return backends.get().submit(variables);
        } catch (RuntimeException failure) {
            copilot.revoke(variables);
            log.error("Runner dispatch failed: workflow={}", variables.get("WORKFLOW"), failure);
            throw new IllegalStateException("Failed to dispatch runner job", failure);
        }
    }

    /**
     * Gives jobs on GitHub pull requests a short-lived installation token scoped to the pull request's repository,
     * so runners post as the Arnold GitHub App without holding long-lived GitHub credentials.
     */
    private void addGitHubToken(final Map<String, String> variables) {
        if (github == null) {
            return;
        }
        final var url = variables.getOrDefault("MR_LINK", variables.get("MR_URL"));
        try {
            github.installationTokenForPullRequest(url).ifPresent(token -> variables.put(SCM_GITHUB_TOKEN, token));
        } catch (RuntimeException failure) {
            log.warn("Could not mint a GitHub installation token for the runner job; the runner's own token is used",
                    failure);
        }
    }

    @Override
    public String jobUrl(final long jobId) {
        try {
            return backends.get().jobUrl(jobId);
        } catch (RuntimeException misconfigured) {
            return null;
        }
    }
}
