package com.phonepe.arnold.core.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.arnold.core.clients.HttpClients;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Runs jobs as GitHub Actions workflows.
 *
 * <p>The job is queued in Arnold and the workflow receives only its number as the {@code job} input; the workflow
 * claims it with the runner API key. Job variables therefore never appear in workflow inputs, which are visible to
 * anyone who can read the runner repository's Actions runs.</p>
 */
@Slf4j
public class GitHubActionsRunnerBackend implements RunnerBackend {

    private static final MediaType JSON = MediaType.get("application/json");

    private final RunnerConfiguration.GitHub config;
    private final RunnerJobQueue queue;
    private final ObjectMapper mapper;
    private final OkHttpClient httpClient;
    private final String apiUrl;
    private final String webUrl;

    public GitHubActionsRunnerBackend(final RunnerConfiguration.GitHub config, final RunnerJobQueue queue,
                                      final ObjectMapper mapper, final OkHttpClient httpClient) {
        if (config.getRepository() == null || !config.getRepository().matches("[\\w.-]+/[\\w.-]+")
                || config.getWorkflow() == null || config.getWorkflow().isBlank()
                || config.getRef() == null || config.getRef().isBlank()
                || config.getToken() == null || config.getToken().isBlank()
                || config.getApiUrl() == null || config.getApiUrl().isBlank()) {
            throw new IllegalStateException("runner.github requires apiUrl, repository (owner/name), workflow, ref and token");
        }
        this.config = config;
        this.queue = queue;
        this.mapper = mapper;
        this.httpClient = httpClient;
        this.apiUrl = HttpClients.trimTrailingSlash(config.getApiUrl());
        this.webUrl = webUrl(apiUrl);
    }

    static String webUrl(final String apiUrl) {
        if (apiUrl.equals("https://api.github.com")) {
            return "https://github.com";
        }
        return apiUrl.replaceFirst("/api/v3$", "");
    }

    @Override
    public RunnerMode mode() {
        return RunnerMode.GITHUB;
    }

    @Override
    public long submit(final Map<String, String> variables) {
        final var job = queue.enqueue(variables);
        try {
            return dispatch(job.number());
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to dispatch GitHub workflow", failure);
        }
    }

    private long dispatch(final long jobNumber) throws IOException {
        final var body = mapper.writeValueAsString(Map.of(
                "ref", config.getRef(),
                "inputs", Map.of("job", Long.toString(jobNumber)),
                "return_run_details", true));
        final var request = new Request.Builder()
                .url(apiUrl + "/repos/" + config.getRepository() + "/actions/workflows/"
                        + URLEncoder.encode(config.getWorkflow(), StandardCharsets.UTF_8) + "/dispatches")
                .header("Authorization", "Bearer " + config.getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .post(RequestBody.create(body, JSON))
                .build();
        try (var response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("GitHub rejected workflow dispatch: " + response.code());
            }
            final var payload = response.body() == null ? "" : response.body().string();
            final var runId = payload.isBlank() ? 0 : mapper.readTree(payload).path("workflow_run_id").asLong(0);
            log.info("GitHub workflow dispatched: repository={}, job={}, run={}", config.getRepository(), jobNumber, runId);
            return runId;
        }
    }

    @Override
    public String jobUrl(final long jobId) {
        final var base = webUrl + "/" + config.getRepository() + "/actions";
        return jobId > 0 ? base + "/runs/" + jobId : base + "/workflows/" + config.getWorkflow();
    }
}
