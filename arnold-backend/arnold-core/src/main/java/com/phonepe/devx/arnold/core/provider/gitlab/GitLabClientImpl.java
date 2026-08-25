package com.phonepe.devx.arnold.core.provider.gitlab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.devx.arnold.core.config.GitLabConfiguration;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.devx.arnold.core.model.vcs.PublishedComment;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
public class GitLabClientImpl implements GitLabClient {
    private static final MediaType JSON = MediaType.get("application/json");
    private static final String MERGE_REQUEST_PATH = "/-/merge_requests/";

    private final GitLabConfiguration configuration;
    private final ObjectMapper mapper;
    private final OkHttpClient httpClient;

    @Inject
    public GitLabClientImpl(final @Config GitLabConfiguration configuration, final ObjectMapper mapper) {
        this.configuration = configuration;
        this.mapper = mapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(configuration.requestTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(configuration.requestTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(configuration.requestTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public OAuthToken exchangeAuthorizationCode(final String code) {
        final var body = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", configuration.clientId())
                .add("client_secret", configuration.clientSecret())
                .add("code", code)
                .add("redirect_uri", configuration.callbackUrl())
                .build();
        return execute(new Request.Builder().url(endpoint("/oauth/token")).post(body).build(), TokenResponse.class)
                .toToken();
    }

    @Override
    public OAuthToken refreshAccessToken(final String refreshToken) {
        final var body = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", configuration.clientId())
                .add("client_secret", configuration.clientSecret())
                .add("refresh_token", refreshToken)
                .add("redirect_uri", configuration.callbackUrl())
                .build();
        return execute(new Request.Builder().url(endpoint("/oauth/token")).post(body).build(), TokenResponse.class)
                .toToken();
    }

    @Override
    public GitLabUser currentUser(final String accessToken) {
        final var user = execute(apiRequest("/user", accessToken).get().build(), UserResponse.class);
        return new GitLabUser(user.id(), user.username());
    }

    @Override
    public MergeRequestResponse resolveMergeRequest(final String url, final String accessToken) {
        final var reference = parseMergeRequestUrl(url);
        final var record = getMergeRequest(reference.projectPath(), reference.iid(), accessToken);
        return toResponse(record);
    }

    @Override
    public ChangeRequestRecord getMergeRequest(final long projectId, final long mergeRequestIid,
                                               final String accessToken) {
        return getMergeRequest(Long.toString(projectId), mergeRequestIid, accessToken);
    }

    @Override
    public List<ChangeRequestRecord> listMergeRequests(final long providerUserId,
                                                       final MergeRequestRole role,
                                                       final String state,
                                                       final Long updatedAfter,
                                                       final String accessToken) {
        final var url = apiRequest("/merge_requests", accessToken).build().url().newBuilder()
                .addQueryParameter("scope", "all")
                .addQueryParameter("state", state)
                .addQueryParameter("per_page", "100")
                .addQueryParameter("order_by", "updated_at")
                .addQueryParameter("sort", "asc")
                .addQueryParameter(roleParameter(role), Long.toString(providerUserId));
        if (updatedAfter != null && updatedAfter > 0) {
            url.addQueryParameter("updated_after", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(updatedAfter)));
        }
        var request = new Request.Builder().url(url.build())
                .header("Authorization", "Bearer " + accessToken).get().build();
        final var records = new ArrayList<ChangeRequestRecord>();
        while (request != null) {
            final var page = executeMergeRequestPage(request);
            final var syncedAt = System.currentTimeMillis();
            page.items().stream().map(item -> toRecord(item, projectPath(item.webUrl()), syncedAt))
                    .forEach(records::add);
            if (page.nextPage() == null) {
                request = null;
            } else {
                final var nextUrl = request.url().newBuilder().setQueryParameter("page", page.nextPage()).build();
                request = request.newBuilder().url(nextUrl).build();
            }
        }
        return List.copyOf(records);
    }

    @Override
    public PublishedComment createDiscussion(final long projectId, final long mergeRequestIid, final String body,
                                              final String accessToken) {
        final var json = writeJson(new DiscussionRequest(body));
        final var request = apiRequest("/projects/" + projectId + "/merge_requests/" + mergeRequestIid + "/discussions",
                accessToken).post(RequestBody.create(json, JSON)).build();
        final var discussion = execute(request, DiscussionResponse.class);
        if (discussion.notes() == null || discussion.notes().isEmpty()) {
            throw clientFailure("GitLab returned a discussion without a note");
        }
        final var note = discussion.notes().get(0);
        return new PublishedComment(Long.toString(note.id()), note.id(), discussion.id(), id(note.author()),
                timestamp(note.updatedAt()));
    }

    private Request.Builder apiRequest(final String path, final String accessToken) {
        return new Request.Builder().url(endpoint("/api/v4" + path)).header("Authorization", "Bearer " + accessToken);
    }

    private ChangeRequestRecord getMergeRequest(final String projectReference, final long mergeRequestIid,
                                                final String accessToken) {
        final var path = "/projects/" + encodePathSegment(projectReference)
                + "/merge_requests/" + mergeRequestIid;
        final var mergeRequest = execute(apiRequest(path, accessToken).get().build(), MergeRequest.class);
        return toRecord(mergeRequest, projectPath(mergeRequest.webUrl()), System.currentTimeMillis());
    }

    private MergeRequestResponse toResponse(final ChangeRequestRecord record) {
        return new MergeRequestResponse(record.id(), Long.parseLong(record.repositoryId()),
                Long.parseLong(record.providerId()), record.number(), record.repositoryPath(), record.title(),
                record.description(), record.webUrl(), record.sourceBranch(), record.targetBranch(), record.headSha(),
                record.state(), record.mergeStatus(), record.draft(), record.authorId(), record.assigneeIds(),
                record.reviewerIds(), record.labels(), record.createdAt(), record.mergedAt(), record.closedAt(),
                record.hasConflicts(), record.blockingDiscussionsResolved(), record.providerUpdatedAt(),
                record.lastSyncedAt());
    }

    private ChangeRequestRecord toRecord(final MergeRequest mergeRequest, final String projectPath,
                                         final long syncedAt) {
        final var repositoryId = Long.toString(mergeRequest.projectId());
        return new ChangeRequestRecord(ChangeRequestRecord.key(VersionControlProviderType.GITLAB,
                repositoryId, mergeRequest.iid()), VersionControlProviderType.GITLAB, repositoryId,
                Long.toString(mergeRequest.id()), mergeRequest.iid(), projectPath, mergeRequest.title(),
                mergeRequest.description(), mergeRequest.webUrl(), mergeRequest.sourceBranch(),
                mergeRequest.targetBranch(), mergeRequest.sha(), mergeRequest.state(),
                mergeRequest.detailedMergeStatus(), mergeRequest.draft(), id(mergeRequest.author()),
                ids(mergeRequest.assignees()), ids(mergeRequest.reviewers()), list(mergeRequest.labels()),
                optionalTimestamp(mergeRequest.createdAt()), optionalTimestamp(mergeRequest.mergedAt()),
                optionalTimestamp(mergeRequest.closedAt()), mergeRequest.hasConflicts(),
                !Boolean.FALSE.equals(mergeRequest.blockingDiscussionsResolved()),
                timestamp(mergeRequest.updatedAt()), syncedAt);
    }

    private MergeRequestPage executeMergeRequestPage(final Request request) {
        try (Response response = executeRequest(request)) {
            if (response.body() == null) {
                throw clientFailure("GitLab returned an empty response");
            }
            final List<MergeRequest> items = mapper.readValue(response.body().byteStream(),
                    mapper.getTypeFactory().constructCollectionType(List.class, MergeRequest.class));
            final var nextPage = response.header("X-Next-Page");
            return new MergeRequestPage(items, nextPage == null || nextPage.isBlank() ? null : nextPage);
        } catch (IOException e) {
            throw ArnoldError.propagate(e).errorCode(ArnoldErrorCode.CLIENT_FAILURE)
                    .message("Failed to call GitLab").build();
        }
    }

    private String endpoint(final String path) {
        return configuration.baseUrl().replaceAll("/+$", "") + path;
    }

    private <T> T execute(final Request request, final Class<T> responseType) {
        try (Response response = executeRequest(request)) {
            if (response.body() == null) {
                throw clientFailure("GitLab returned an empty response");
            }
            return mapper.readValue(response.body().byteStream(), responseType);
        } catch (IOException e) {
            throw ArnoldError.propagate(e).errorCode(ArnoldErrorCode.CLIENT_FAILURE)
                    .message("Failed to call GitLab").build();
        }
    }

    private Response executeRequest(final Request request) throws IOException {
        for (var attempt = 0; attempt < 3; attempt++) {
            final var response = httpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                return response;
            }
            final var code = response.code();
            final var retryAfter = response.header("Retry-After");
            response.close();
            if (attempt == 2 || (code != 429 && code < 500)) {
                throw clientFailure("GitLab request failed with HTTP " + code);
            }
            try {
                final var delayMs = retryAfter == null ? 250L * (attempt + 1)
                        : Math.min(30_000L, Long.parseLong(retryAfter) * 1000L);
                Thread.sleep(delayMs);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while retrying GitLab", interrupted);
            } catch (NumberFormatException ignored) {
                // Retry immediately when GitLab supplies a non-numeric Retry-After value.
            }
        }
        throw clientFailure("GitLab request failed");
    }

    private String writeJson(final Object body) {
        try {
            return mapper.writeValueAsString(body);
        } catch (IOException e) {
            throw ArnoldError.propagate(e).errorCode(ArnoldErrorCode.JSON_SER_ERROR).build();
        }
    }

    private MergeRequestReference parseMergeRequestUrl(final String url) {
        try {
            final var uri = URI.create(url);
            final var configuredHost = URI.create(configuration.baseUrl()).getHost();
            final var configuredScheme = URI.create(configuration.baseUrl()).getScheme();
            if (!configuredScheme.equalsIgnoreCase(uri.getScheme()) || !configuredHost.equalsIgnoreCase(uri.getHost())
                    || effectivePort(URI.create(configuration.baseUrl())) != effectivePort(uri)) {
                throw new IllegalArgumentException("Unexpected GitLab host");
            }
            final var path = uri.getPath();
            final var marker = path.lastIndexOf(MERGE_REQUEST_PATH);
            if (marker <= 1) {
                throw new IllegalArgumentException("Missing merge request path");
            }
            final var projectPath = path.substring(1, marker);
            final var iid = Long.parseLong(path.substring(marker + MERGE_REQUEST_PATH.length()).replaceAll("/+$", ""));
            return new MergeRequestReference(projectPath, iid);
        } catch (RuntimeException e) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                    .message("Invalid GitLab merge request URL").build();
        }
    }

    private static String encodePathSegment(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static int effectivePort(final URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static long timestamp(final String value) {
        return OffsetDateTime.parse(value).toInstant().toEpochMilli();
    }

    private static long optionalTimestamp(final String value) {
        return value == null || value.isBlank() ? 0 : timestamp(value);
    }

    private static String roleParameter(final MergeRequestRole role) {
        return switch (role) {
            case AUTHOR -> "author_id";
            case ASSIGNEE -> "assignee_id";
            case REVIEWER -> "reviewer_id";
        };
    }

    private static String projectPath(final String webUrl) {
        try {
            final var path = URI.create(webUrl).getPath();
            final var marker = path.lastIndexOf(MERGE_REQUEST_PATH);
            if (marker <= 1) {
                throw new IllegalArgumentException("Missing merge request path");
            }
            return path.substring(1, marker);
        } catch (RuntimeException failure) {
            throw clientFailure("GitLab returned an invalid merge request URL");
        }
    }

    private static Long id(final UserResponse user) {
        return user == null ? null : user.id();
    }

    private static List<Long> ids(final List<UserResponse> users) {
        return users == null ? List.of() : users.stream().map(UserResponse::id).toList();
    }

    private static <T> List<T> list(final List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static RuntimeException clientFailure(final String message) {
        return ArnoldError.raise(ArnoldErrorCode.CLIENT_FAILURE).message(message).build();
    }

    private record MergeRequestReference(String projectPath, long iid) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("refresh_token") String refreshToken,
                                 @JsonProperty("expires_in") long expiresIn,
                                 String scope) {
        private OAuthToken toToken() {
            return new OAuthToken(accessToken, refreshToken, System.currentTimeMillis() + expiresIn * 1000, scope);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UserResponse(long id, String username) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MergeRequest(long id, @JsonProperty("project_id") long projectId, long iid, String title,
                                String description, @JsonProperty("web_url") String webUrl,
                                @JsonProperty("source_branch") String sourceBranch,
                                @JsonProperty("target_branch") String targetBranch, String sha, String state,
                                @JsonProperty("detailed_merge_status") String detailedMergeStatus, boolean draft,
                                UserResponse author,
                                List<UserResponse> assignees, List<UserResponse> reviewers, List<String> labels,
                                @JsonProperty("created_at") String createdAt,
                                @JsonProperty("merged_at") String mergedAt,
                                @JsonProperty("closed_at") String closedAt,
                                @JsonProperty("has_conflicts") boolean hasConflicts,
                                @JsonProperty("blocking_discussions_resolved") Boolean blockingDiscussionsResolved,
                                @JsonProperty("updated_at") String updatedAt) {
    }

    private record MergeRequestPage(List<MergeRequest> items, String nextPage) {
    }

    private record DiscussionRequest(String body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DiscussionResponse(String id, List<NoteResponse> notes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NoteResponse(long id, UserResponse author, @JsonProperty("updated_at") String updatedAt) {
    }
}
