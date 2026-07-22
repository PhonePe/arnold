package com.phonepe.devx.arnold.core.provider.gitlab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.devx.arnold.core.config.GitLabConfiguration;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
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
        final var path = "/projects/" + encodePathSegment(reference.projectPath())
                + "/merge_requests/" + reference.iid();
        final var mergeRequest = execute(apiRequest(path, accessToken).get().build(), MergeRequest.class);
        final var syncedAt = System.currentTimeMillis();
        return new MergeRequestResponse(ChangeRequestRecord.key(VersionControlProviderType.GITLAB,
                Long.toString(mergeRequest.projectId()), mergeRequest.iid()), mergeRequest.projectId(), mergeRequest.id(),
                mergeRequest.iid(), reference.projectPath(), mergeRequest.title(), mergeRequest.description(),
                mergeRequest.webUrl(), mergeRequest.sourceBranch(), mergeRequest.targetBranch(), mergeRequest.sha(),
                mergeRequest.state(), mergeRequest.detailedMergeStatus(), mergeRequest.draft(), id(mergeRequest.author()),
                ids(mergeRequest.assignees()), ids(mergeRequest.reviewers()), list(mergeRequest.labels()),
                timestamp(mergeRequest.updatedAt()), syncedAt);
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

    private String endpoint(final String path) {
        return configuration.baseUrl().replaceAll("/+$", "") + path;
    }

    private <T> T execute(final Request request, final Class<T> responseType) {
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw clientFailure("GitLab request failed with HTTP " + response.code());
            }
            if (response.body() == null) {
                throw clientFailure("GitLab returned an empty response");
            }
            return mapper.readValue(response.body().byteStream(), responseType);
        } catch (IOException e) {
            throw ArnoldError.propagate(e).errorCode(ArnoldErrorCode.CLIENT_FAILURE)
                    .message("Failed to call GitLab").build();
        }
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
                                @JsonProperty("updated_at") String updatedAt) {
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
