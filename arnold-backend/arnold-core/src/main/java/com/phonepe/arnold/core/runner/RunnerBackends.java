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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.clients.HttpClients;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.storage.aerospike.AerospikeRunnerJobStore;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Builds the {@link RunnerBackend} for the configured mode on first use, so only that mode's settings are validated.
 */
@Singleton
public class RunnerBackends {

    private final ArnoldCoreConfiguration configuration;
    private final ObjectMapper mapper;
    private final Provider<AerospikeRunnerJobStore> jobStore;
    private volatile RunnerBackend backend;
    private volatile RunnerJobQueue queue;

    @Inject
    public RunnerBackends(final ArnoldCoreConfiguration configuration, final ObjectMapper mapper,
                          final Provider<AerospikeRunnerJobStore> jobStore) {
        this.configuration = configuration;
        this.mapper = mapper;
        this.jobStore = jobStore;
    }

    public RunnerBackend get() {
        var current = backend;
        if (current == null) {
            synchronized (this) {
                current = backend;
                if (current == null) {
                    current = create(effective(configuration));
                    backend = current;
                }
            }
        }
        return current;
    }

    /**
     * @return the job queue used by modes in which runners claim jobs from Arnold
     * @throws ArnoldError when the configured mode does not use the queue
     */
    public RunnerJobQueue queue() {
        get();
        final var current = queue;
        if (current == null) {
            throw ArnoldError.raise(ArnoldErrorCode.OPERATION_NOT_ALLOWED)
                    .message("The configured runner mode does not use the job queue").build();
        }
        return current;
    }

    /**
     * Resolves the runner configuration, falling back to the legacy {@code gitlabConfiguration.clio} block.
     */
    static RunnerConfiguration effective(final ArnoldCoreConfiguration configuration) {
        final var gitlab = configuration.getGitlabConfiguration();
        if (configuration.getRunner() != null) {
            final var runner = configuration.getRunner();
            if (runner.getGitlab().getBaseUrl() == null && gitlab != null) {
                runner.getGitlab().setBaseUrl(gitlab.baseUrl());
            }
            return runner;
        }
        final var runner = new RunnerConfiguration();
        if (gitlab != null && gitlab.clio() != null) {
            runner.setMode(RunnerMode.GITLAB);
            runner.getGitlab().setBaseUrl(gitlab.baseUrl());
            runner.getGitlab().setProjectId(gitlab.clio().projectId());
            runner.getGitlab().setProjectPath(gitlab.clio().projectPath());
            runner.getGitlab().setRef(gitlab.clio().ref());
            runner.getGitlab().setTriggerToken(gitlab.clio().triggerToken());
        }
        return runner;
    }

    private RunnerBackend create(final RunnerConfiguration runner) {
        return switch (runner.getMode()) {
            case LOCAL -> new LocalQueueRunnerBackend(createQueue(runner));
            case GITLAB -> new GitLabPipelineRunnerBackend(runner.getGitlab(), mapper, HttpClients.create(30_000));
            case GITHUB -> new GitHubActionsRunnerBackend(runner.getGithub(), createQueue(runner), mapper,
                    HttpClients.create(runner.getGithub().getRequestTimeoutMs()));
        };
    }

    private RunnerJobQueue createQueue(final RunnerConfiguration runner) {
        final var created = new RunnerJobQueue(jobStore.get(), runner.getLocal());
        queue = created;
        return created;
    }
}
