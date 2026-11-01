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
package com.phonepe.arnold.core.provider.github;

import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.config.RepositorySyncConfiguration;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.model.sync.RepositorySyncResult;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.RepositorySyncProvider;
import com.phonepe.arnold.core.sync.RepositoryChangeRequestImporter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Optional;

/**
 * Imports open pull requests from every repository of every installation of the GitHub App.
 */
@Slf4j
@Singleton
public class GitHubRepositorySyncProvider implements RepositorySyncProvider {
    private final GitHubIntegration integration;
    private final RepositoryChangeRequestImporter importer;
    private final RepositorySyncConfiguration configuration;

    @Inject
    public GitHubRepositorySyncProvider(final GitHubIntegration integration,
                                        final RepositoryChangeRequestImporter importer,
                                        final ArnoldCoreConfiguration configuration) {
        this(integration, importer, configuration.getRepositorySync());
    }

    GitHubRepositorySyncProvider(final GitHubIntegration integration, final RepositoryChangeRequestImporter importer,
                                 final RepositorySyncConfiguration configuration) {
        this.integration = integration;
        this.importer = importer;
        this.configuration = configuration == null ? RepositorySyncConfiguration.defaults() : configuration;
    }

    @Override
    public VersionControlProviderType type() {
        return VersionControlProviderType.GITHUB;
    }

    @Override
    public boolean available() {
        return integration.appConfigured();
    }

    @Override
    public RepositorySyncResult synchronize() {
        final var client = integration.client();
        final var repositoryTokens = new LinkedHashMap<String, String>();
        final var open = new ArrayList<ChangeRequestRecord>();
        String failure = null;
        try {
            for (final var installationId : integration.installations()) {
                final var token = integration.installationAccessToken(installationId);
                for (final var repository : client.installationRepositories(token)) {
                    if (repository.archived()) {
                        continue;
                    }
                    client.openPullRequests(repository.fullName(), token).stream()
                            .map(GitHubWebhookService::toRecord)
                            .forEach(open::add);
                    repositoryTokens.put(Long.toString(repository.id()), token);
                }
            }
        } catch (ArnoldError error) {
            final var appFailure = integration.appAuthenticationFailure(error);
            if (GitHubIntegration.isAppAuthenticationFailure(appFailure)) {
                failure = appFailure.getMessage();
            } else if (RepositorySyncProvider.rateLimited(error)) {
                log.warn("GitHub rate limit reached after {} repositories; continuing next run",
                        repositoryTokens.size());
                failure = "GitHub rate limit reached; the next run continues";
            } else {
                throw error;
            }
        }
        try {
            final var imported = importer.importOpen(type(), repositoryTokens.keySet(), open, failure == null,
                    configuration.maxReconcilePerRun(), stored -> refresh(stored, repositoryTokens));
            return new RepositorySyncResult(repositoryTokens.size(), imported, failure);
        } catch (ArnoldError error) {
            final var appFailure = integration.appAuthenticationFailure(error);
            if (GitHubIntegration.isAppAuthenticationFailure(appFailure)) {
                return new RepositorySyncResult(repositoryTokens.size(), open.size(), appFailure.getMessage());
            }
            if (!RepositorySyncProvider.rateLimited(error)) {
                throw error;
            }
            return new RepositorySyncResult(repositoryTokens.size(), open.size(),
                    "GitHub rate limit reached; the next run continues");
        }
    }

    private Optional<ChangeRequestRecord> refresh(final ChangeRequestRecord stored,
                                                  final java.util.Map<String, String> repositoryTokens) {
        try {
            return Optional.of(GitHubWebhookService.toRecord(integration.client().getPullRequest(
                    Long.parseLong(stored.repositoryId()), stored.number(),
                    repositoryTokens.get(stored.repositoryId()))));
        } catch (ArnoldError error) {
            if (RepositorySyncProvider.notFound(error)) {
                log.info("GitHub pull request {} is no longer available", stored.id());
                return Optional.empty();
            }
            throw error;
        }
    }
}
