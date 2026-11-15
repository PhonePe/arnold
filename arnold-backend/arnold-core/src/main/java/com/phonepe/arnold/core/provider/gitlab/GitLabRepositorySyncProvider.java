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
package com.phonepe.arnold.core.provider.gitlab;

import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.config.GitLabConfiguration;
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
import java.util.List;
import java.util.Optional;

import static com.phonepe.arnold.core.common.Constants.STATE_OPENED;

/**
 * Imports open merge requests with {@code gitlabConfiguration.serviceToken}, either from the configured groups
 * (including subgroups) or from every project the token is a member of.
 */
@Slf4j
@Singleton
public class GitLabRepositorySyncProvider implements RepositorySyncProvider {
    private static final String RATE_LIMITED = "GitLab rate limit reached; the next run continues";

    private final GitLabConfiguration gitLabConfiguration;
    private final RepositorySyncConfiguration configuration;
    private final GitLabClient client;
    private final RepositoryChangeRequestImporter importer;

    @Inject
    public GitLabRepositorySyncProvider(final ArnoldCoreConfiguration configuration, final GitLabClient client,
                                        final RepositoryChangeRequestImporter importer) {
        this(configuration.getGitlabConfiguration(), configuration.getRepositorySync(), client, importer);
    }

    GitLabRepositorySyncProvider(final GitLabConfiguration gitLabConfiguration,
                                 final RepositorySyncConfiguration configuration, final GitLabClient client,
                                 final RepositoryChangeRequestImporter importer) {
        this.gitLabConfiguration = gitLabConfiguration;
        this.configuration = configuration == null ? RepositorySyncConfiguration.defaults() : configuration;
        this.client = client;
        this.importer = importer;
    }

    @Override
    public VersionControlProviderType type() {
        return VersionControlProviderType.GITLAB;
    }

    @Override
    public boolean available() {
        return gitLabConfiguration != null && gitLabConfiguration.serviceToken() != null
                && !gitLabConfiguration.serviceToken().isBlank();
    }

    @Override
    public RepositorySyncResult synchronize() {
        final var token = gitLabConfiguration.serviceToken();
        final var projects = new LinkedHashMap<Long, GitLabClient.GitLabProject>();
        final var open = new ArrayList<ChangeRequestRecord>();
        final var synced = new ArrayList<String>();
        String failure = null;
        try {
            final List<String> groups = configuration.gitlabGroups().isEmpty()
                    ? java.util.Collections.singletonList(null) : configuration.gitlabGroups();
            for (final var group : groups) {
                client.listProjects(group, token).stream()
                        .filter(project -> !project.archived())
                        .forEach(project -> projects.putIfAbsent(project.id(), project));
            }
            for (final var project : projects.values()) {
                open.addAll(client.listProjectMergeRequests(project.id(), STATE_OPENED, token));
                synced.add(Long.toString(project.id()));
            }
        } catch (ArnoldError error) {
            if (!RepositorySyncProvider.rateLimited(error)) {
                throw error;
            }
            log.warn("GitLab rate limit reached after {} projects; continuing next run", synced.size());
            failure = RATE_LIMITED;
        }
        try {
            final var imported = importer.importOpen(type(), synced, open, failure == null,
                    configuration.maxReconcilePerRun(), stored -> refresh(stored, token));
            return new RepositorySyncResult(synced.size(), imported, failure);
        } catch (ArnoldError error) {
            if (!RepositorySyncProvider.rateLimited(error)) {
                throw error;
            }
            return new RepositorySyncResult(synced.size(), open.size(), RATE_LIMITED);
        }
    }

    private Optional<ChangeRequestRecord> refresh(final ChangeRequestRecord stored, final String token) {
        try {
            return Optional.of(client.getMergeRequest(Long.parseLong(stored.repositoryId()), stored.number(), token));
        } catch (ArnoldError error) {
            if (RepositorySyncProvider.notFound(error)) {
                log.info("GitLab merge request {} is no longer available", stored.id());
                return Optional.empty();
            }
            throw error;
        }
    }
}
