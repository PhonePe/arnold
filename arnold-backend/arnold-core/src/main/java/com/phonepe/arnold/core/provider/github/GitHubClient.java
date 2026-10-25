package com.phonepe.arnold.core.provider.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.arnold.core.clients.HttpClients;
import com.phonepe.arnold.core.config.GitHubConfiguration;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.PublishedComment;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.arnold.models.comment.DiscussionPosition;
import com.phonepe.arnold.models.comment.PublishReviewRequest;
import com.phonepe.arnold.models.mergerequest.MergeRequestDiffVersionResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestFilesResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestReviewResponse;
import com.phonepe.arnold.models.mergerequest.ReviewerCandidateResponse;
import com.phonepe.arnold.models.mergerequest.ReviewerStatusResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * GitHub REST and GraphQL client for pull request review operations.
 *
 * <p>Arnold identifies a pull request by the numeric repository id and PR number. Calls that need
 * {@code owner/name} resolve it once per repository through {@code GET /repositories/{id}}.</p>
 */
@Slf4j
public class GitHubClient {

    private static final MediaType JSON = MediaType.get("application/json");
    private static final String API_VERSION = "2022-11-28";
    private static final String GRAPHQL_THREADS = """
            query($owner:String!,$name:String!,$number:Int!,$after:String){
              repository(owner:$owner,name:$name){pullRequest(number:$number){
                reviewThreads(first:50,after:$after){pageInfo{hasNextPage endCursor} nodes{
                  id isResolved path line originalLine startLine diffSide
                  comments(first:100){nodes{databaseId body createdAt updatedAt
                    author{login ... on User{databaseId}} pullRequestReview{state}}}}}}}}""";

    private final GitHubConfiguration config;
    private final ObjectMapper mapper;
    private final OkHttpClient http;
    private final Clock clock;
    private final String apiUrl;
    private final String webUrl;
    private final String graphqlUrl;
    private final Pattern pullUrl;
    private final Map<Long, String> repositoryNames = new ConcurrentHashMap<>();

    public GitHubClient(final GitHubConfiguration config, final ObjectMapper mapper) {
        this(config, mapper, HttpClients.create(config.getRequestTimeoutMs()), Clock.systemUTC());
    }

    GitHubClient(final GitHubConfiguration config, final ObjectMapper mapper, final OkHttpClient http,
                 final Clock clock) {
        this.config = config;
        this.mapper = mapper;
        this.http = http;
        this.clock = clock;
        this.apiUrl = HttpClients.trimTrailingSlash(config.getApiUrl());
        this.webUrl = HttpClients.trimTrailingSlash(config.getWebUrl());
        this.graphqlUrl = apiUrl.endsWith("/api/v3") ? apiUrl.replaceFirst("/api/v3$", "/api/graphql")
                : apiUrl + "/graphql";
        this.pullUrl = Pattern.compile("^" + Pattern.quote(webUrl) + "/([\\w.-]+)/([\\w.-]+)/pull/(\\d+)(?:[/?#].*)?$");
    }

    // ---------------------------------------------------------------- OAuth (user-to-server tokens)

    public record OAuthToken(String accessToken, String refreshToken, Long expiresAt, String scope) {
    }

    public record GitHubUser(long id, String login, String avatarUrl) {
    }

    public OAuthToken exchangeCode(final String code) {
        return token(new FormBody.Builder()
                .add("client_id", config.getClientId())
                .add("client_secret", config.getClientSecret())
                .add("code", code)
                .add("redirect_uri", config.getCallbackUrl())
                .build());
    }

    public OAuthToken refresh(final String refreshToken) {
        return token(new FormBody.Builder()
                .add("client_id", config.getClientId())
                .add("client_secret", config.getClientSecret())
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build());
    }

    private OAuthToken token(final FormBody body) {
        final var json = json(new Request.Builder().url(webUrl + "/login/oauth/access_token")
                .header("Accept", "application/json").post(body).build());
        if (json.hasNonNull("error") || !json.hasNonNull("access_token")) {
            throw ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR)
                    .message("GitHub rejected the authorization: " + json.path("error").asText("no token")).build();
        }
        final var expiresIn = json.path("expires_in").asLong(0);
        return new OAuthToken(json.get("access_token").asText(), text(json, "refresh_token"),
                expiresIn > 0 ? clock.millis() + expiresIn * 1000 : null, text(json, "scope"));
    }

    public GitHubUser currentUser(final String token) {
        final var user = get("/user", token);
        return new GitHubUser(user.path("id").asLong(), user.path("login").asText(), text(user, "avatar_url"));
    }

    // ---------------------------------------------------------------- Installation tokens

    /**
     * @return the installation id of the app on {@code owner/name}
     */
    public long installationId(final String fullName, final GitHubAppAuth app) {
        return get("/repos/" + fullName + "/installation", app.jwt()).path("id").asLong();
    }

    public OAuthToken installationToken(final long installationId, final List<String> repositories,
                                        final GitHubAppAuth app) {
        final var body = repositories == null || repositories.isEmpty() ? Map.of() : Map.of("repositories", repositories);
        final var json = send("POST", "/app/installations/" + installationId + "/access_tokens", app.jwt(), body);
        return new OAuthToken(json.path("token").asText(), null,
                Instant.parse(json.path("expires_at").asText()).toEpochMilli(), null);
    }

    // ---------------------------------------------------------------- Pull requests

    public MergeRequestResponse resolvePullRequest(final String url, final String token) {
        final var matcher = pullUrl.matcher(url == null ? "" : url.trim());
        if (!matcher.matches()) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                    .message("Not a pull request URL on " + webUrl).build();
        }
        return toResponse(get("/repos/" + matcher.group(1) + "/" + matcher.group(2) + "/pulls/" + matcher.group(3),
                token));
    }

    public MergeRequestResponse getPullRequest(final long repositoryId, final long number, final String token) {
        return toResponse(pull(repositoryId, number, token));
    }

    public MergeRequestResponse addReviewer(final long repositoryId, final long number, final String login,
                                            final String token) {
        return toResponse(send("POST", pullPath(repositoryId, number, token) + "/requested_reviewers", token,
                Map.of("reviewers", List.of(login))));
    }

    public MergeRequestResponse removeReviewer(final long repositoryId, final long number, final long reviewerId,
                                               final String token) {
        final var login = get("/user/" + reviewerId, token).path("login").asText();
        return toResponse(send("DELETE", pullPath(repositoryId, number, token) + "/requested_reviewers", token,
                Map.of("reviewers", List.of(login))));
    }

    public List<ReviewerCandidateResponse> searchReviewers(final long repositoryId, final String query,
                                                           final String token) {
        final var needle = query == null ? "" : query.toLowerCase(Locale.ROOT);
        final var candidates = new ArrayList<ReviewerCandidateResponse>();
        for (final var user : get("/repos/" + repository(repositoryId, token) + "/assignees?per_page=100", token)) {
            final var login = user.path("login").asText();
            if (login.toLowerCase(Locale.ROOT).contains(needle)) {
                candidates.add(new ReviewerCandidateResponse(user.path("id").asLong(), login, login,
                        text(user, "avatar_url")));
            }
            if (candidates.size() == 20) {
                break;
            }
        }
        return candidates;
    }

    public MergeRequestReviewResponse review(final long repositoryId, final long number, final String token) {
        final var pull = pull(repositoryId, number, token);
        final var path = pullPath(repositoryId, number, token);
        final var latest = new LinkedHashMap<Long, JsonNode>();
        for (final var review : get(path + "/reviews?per_page=100", token)) {
            final var state = review.path("state").asText();
            if (!"PENDING".equals(state) && !"DISMISSED".equals(state)) {
                latest.put(review.path("user").path("id").asLong(), review);
            }
        }
        final var reviewers = new ArrayList<ReviewerStatusResponse>();
        for (final var requested : pull.path("requested_reviewers")) {
            reviewers.add(reviewer(requested, "pending"));
        }
        var changesRequested = false;
        var approvals = 0;
        for (final var review : latest.values()) {
            final var state = review.path("state").asText();
            changesRequested |= "CHANGES_REQUESTED".equals(state);
            approvals += "APPROVED".equals(state) ? 1 : 0;
            reviewers.add(reviewer(review.path("user"), switch (state) {
                case "APPROVED" -> "approved";
                case "CHANGES_REQUESTED" -> "requested_changes";
                default -> "reviewed";
            }));
        }
        final var mergeableState = pull.path("mergeable_state").asText("unknown");
        return new MergeRequestReviewResponse(mergeableState, "dirty".equals(mergeableState), true,
                checksStatus(repositoryId, pull.path("head").path("sha").asText(), token),
                pull.path("html_url").asText() + "/checks", 0, 0, approvals > 0 && !changesRequested, reviewers);
    }

    private String checksStatus(final long repositoryId, final String sha, final String token) {
        final var repo = repository(repositoryId, token);
        final var combined = get("/repos/" + repo + "/commits/" + sha + "/status", token);
        final var runs = get("/repos/" + repo + "/commits/" + sha + "/check-runs?per_page=100", token)
                .path("check_runs");
        var failed = "failure".equals(combined.path("state").asText()) || "error".equals(combined.path("state").asText());
        var running = combined.path("total_count").asInt() > 0 && "pending".equals(combined.path("state").asText());
        for (final var run : runs) {
            final var conclusion = run.path("conclusion").asText("");
            failed |= List.of("failure", "timed_out", "cancelled", "action_required").contains(conclusion);
            running |= !"completed".equals(run.path("status").asText());
        }
        if (failed) {
            return "failed";
        }
        if (running) {
            return "running";
        }
        return runs.isEmpty() && combined.path("total_count").asInt() == 0 ? null : "success";
    }

    public String diff(final long repositoryId, final long number, final String token) {
        return text(new Request.Builder().url(apiUrl + pullPath(repositoryId, number, token)), token,
                "application/vnd.github.diff");
    }

    /**
     * GitHub has no merge request versions; the current head is exposed as a single version against the merge base.
     */
    public List<MergeRequestDiffVersionResponse> diffVersions(final long repositoryId, final long number,
                                                              final String token) {
        final var pull = pull(repositoryId, number, token);
        final var head = pull.path("head").path("sha").asText();
        final var compare = get("/repos/" + repository(repositoryId, token) + "/compare/"
                + pull.path("base").path("sha").asText() + "..." + head, token);
        final var mergeBase = compare.path("merge_base_commit").path("sha").asText();
        return List.of(new MergeRequestDiffVersionResponse(1, mergeBase, mergeBase, head,
                instant(pull, "updated_at"), pull.path("title").asText()));
    }

    public String diffVersion(final long repositoryId, final long number, final long versionId, final String token) {
        if (versionId != 1) {
            throw ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build();
        }
        return diff(repositoryId, number, token);
    }

    public MergeRequestFilesResponse files(final long repositoryId, final long number, final String oldPath,
                                           final String newPath, final String baseSha, final String headSha,
                                           final String token) {
        var base = baseSha;
        var head = headSha;
        if (base == null || head == null) {
            final var pull = pull(repositoryId, number, token);
            base = base == null ? pull.path("base").path("sha").asText() : base;
            head = head == null ? pull.path("head").path("sha").asText() : head;
        }
        final var repo = repository(repositoryId, token);
        return new MergeRequestFilesResponse(content(repo, oldPath, base, token), content(repo, newPath, head, token));
    }

    private String content(final String repo, final String path, final String ref, final String token) {
        final var url = HttpUrl.get(apiUrl + "/repos/" + repo + "/contents/" + encodePath(path)).newBuilder()
                .addQueryParameter("ref", ref).build();
        try (var response = http.newCall(base(new Request.Builder().url(url), token)
                .header("Accept", "application/vnd.github.raw").get().build()).execute()) {
            if (response.code() == 404) {
                return "";
            }
            ensureSuccess(response);
            return response.body() == null ? "" : response.body().string();
        } catch (IOException e) {
            throw failure(e);
        }
    }

    // ---------------------------------------------------------------- Discussions

    public List<PublishedComment> discussions(final long repositoryId, final long number, final String token) {
        final var comments = new ArrayList<PublishedComment>();
        for (final var comment : get(issuePath(repositoryId, number, token) + "/comments?per_page=100", token)) {
            comments.add(issueComment(comment));
        }
        final var repo = repository(repositoryId, token).split("/");
        String after = null;
        do {
            final var variables = new LinkedHashMap<String, Object>();
            variables.put("owner", repo[0]);
            variables.put("name", repo[1]);
            variables.put("number", number);
            variables.put("after", after);
            final var threads = graphql(GRAPHQL_THREADS, variables, token)
                    .path("repository").path("pullRequest").path("reviewThreads");
            for (final var thread : threads.path("nodes")) {
                for (final var comment : thread.path("comments").path("nodes")) {
                    if (!"PENDING".equals(comment.path("pullRequestReview").path("state").asText())) {
                        comments.add(threadComment(thread, comment));
                    }
                }
            }
            after = threads.path("pageInfo").path("hasNextPage").asBoolean()
                    ? threads.path("pageInfo").path("endCursor").asText() : null;
        } while (after != null);
        return comments;
    }

    public PublishedComment createDiscussion(final CreateDiscussionRequest request, final String token) {
        final var repositoryId = request.getProjectId();
        final var number = request.getMrIid();
        final var discussionId = request.getDiscussionId();
        if (discussionId != null && !discussionId.startsWith("issue:")) {
            final var reply = graphql("""
                    mutation($thread:ID!,$body:String!){addPullRequestReviewThreadReply(input:{
                      pullRequestReviewThreadId:$thread,body:$body}){comment{databaseId}}}""",
                    Map.of("thread", discussionId, "body", request.getBody()), token);
            final var id = reply.path("addPullRequestReviewThreadReply").path("comment").path("databaseId").asLong();
            return reviewComment(get("/repos/" + repository(repositoryId, token) + "/pulls/comments/" + id, token),
                    discussionId, "published", null);
        }
        if (request.getPosition() != null && discussionId == null) {
            final var created = send("POST", pullPath(repositoryId, number, token) + "/comments", token,
                    lineComment(request.getPosition(), request.getBody(), true));
            return reviewComment(created, null, "published", null);
        }
        return issueComment(send("POST", issuePath(repositoryId, number, token) + "/comments", token,
                Map.of("body", request.getBody())));
    }

    // ---------------------------------------------------------------- Pending review (drafts)

    public List<PublishedComment> drafts(final long repositoryId, final long number, final String token,
                                         final String login) {
        final var pending = pendingReview(repositoryId, number, token, login);
        if (pending == null) {
            return List.of();
        }
        final var drafts = new ArrayList<PublishedComment>();
        for (final var comment : get(pullPath(repositoryId, number, token) + "/reviews/"
                + pending.path("id").asLong() + "/comments?per_page=100", token)) {
            drafts.add(reviewComment(comment, null, "draft", comment.path("id").asLong()));
        }
        return drafts;
    }

    public PublishedComment createDraft(final CreateDiscussionRequest request, final String token, final String login) {
        if (request.getPosition() == null) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                    .message("GitHub review drafts must be attached to a line").build();
        }
        final var repositoryId = request.getProjectId();
        final var number = request.getMrIid();
        final var pending = pendingReview(repositoryId, number, token, login);
        if (pending == null) {
            final var body = new LinkedHashMap<String, Object>();
            if (request.getPosition().headSha() != null) {
                body.put("commit_id", request.getPosition().headSha());
            }
            body.put("comments", List.of(lineComment(request.getPosition(), request.getBody(), false)));
            send("POST", pullPath(repositoryId, number, token) + "/reviews", token, body);
        } else {
            final var position = request.getPosition();
            final var variables = new LinkedHashMap<String, Object>();
            variables.put("review", pending.path("node_id").asText());
            variables.put("path", position.newLine() != null ? position.newPath() : position.oldPath());
            variables.put("line", position.newLine() != null ? position.newLine() : position.oldLine());
            variables.put("side", position.newLine() != null ? "RIGHT" : "LEFT");
            variables.put("body", request.getBody());
            graphql("""
                    mutation($review:ID!,$path:String!,$line:Int!,$side:DiffSide!,$body:String!){
                      addPullRequestReviewThread(input:{pullRequestReviewId:$review,path:$path,line:$line,side:$side,
                        body:$body}){thread{id}}}""", variables, token);
        }
        final var drafts = drafts(repositoryId, number, token, login);
        return drafts.stream().max(java.util.Comparator.comparingLong(PublishedComment::numericId))
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.CLIENT_FAILURE)
                        .message("GitHub did not return the created draft").build());
    }

    public PublishedComment updateDraft(final long repositoryId, final long draftId, final String body,
                                        final String token) {
        final var updated = send("PATCH", "/repos/" + repository(repositoryId, token) + "/pulls/comments/" + draftId,
                token, Map.of("body", body));
        return reviewComment(updated, null, "draft", draftId);
    }

    public void deleteDraft(final long repositoryId, final long draftId, final String token) {
        send("DELETE", "/repos/" + repository(repositoryId, token) + "/pulls/comments/" + draftId, token, null);
    }

    public void publishReview(final PublishReviewRequest request, final String token, final String login) {
        final var event = switch (request.getReviewerState()) {
            case "approved" -> "APPROVE";
            case "requested_changes" -> "REQUEST_CHANGES";
            default -> "COMMENT";
        };
        final var body = new LinkedHashMap<String, Object>();
        body.put("event", event);
        if (request.getSummary() != null && !request.getSummary().isBlank()) {
            body.put("body", request.getSummary());
        }
        final var path = pullPath(request.getProjectId(), request.getMrIid(), token) + "/reviews";
        final var pending = pendingReview(request.getProjectId(), request.getMrIid(), token, login);
        if (pending != null) {
            send("POST", path + "/" + pending.path("id").asLong() + "/events", token, body);
        } else {
            if (!"APPROVE".equals(event) && !body.containsKey("body")) {
                throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                        .message("A summary is required when there are no draft comments").build();
            }
            send("POST", path, token, body);
        }
    }

    private JsonNode pendingReview(final long repositoryId, final long number, final String token, final String login) {
        for (final var review : get(pullPath(repositoryId, number, token) + "/reviews?per_page=100", token)) {
            if ("PENDING".equals(review.path("state").asText())
                    && login.equalsIgnoreCase(review.path("user").path("login").asText())) {
                return review;
            }
        }
        return null;
    }

    public void resolveThread(final String threadId, final boolean resolved, final String token) {
        if (threadId == null || threadId.startsWith("issue:")) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                    .message("Only review threads can be resolved on GitHub").build();
        }
        final var mutation = resolved ? "resolveReviewThread" : "unresolveReviewThread";
        graphql("mutation($thread:ID!){" + mutation + "(input:{threadId:$thread}){thread{id}}}",
                Map.of("thread", threadId), token);
    }

    // ---------------------------------------------------------------- Mapping

    /**
     * Maps a pull request object (REST response or webhook payload) to Arnold's change request shape.
     */
    public MergeRequestResponse toResponse(final JsonNode pull) {
        final var repository = pull.path("base").path("repo");
        final var repositoryId = repository.path("id").asLong();
        repositoryNames.putIfAbsent(repositoryId, repository.path("full_name").asText());
        final var number = pull.path("number").asLong();
        final var merged = pull.hasNonNull("merged_at");
        final var state = merged ? "merged" : "open".equals(pull.path("state").asText()) ? "opened" : "closed";
        final var mergeableState = pull.path("mergeable_state").asText(null);
        return new MergeRequestResponse(
                ChangeRequestRecord.key(VersionControlProviderType.GITHUB, Long.toString(repositoryId), number),
                VersionControlProviderType.GITHUB.name(), Long.toString(repositoryId), pull.path("id").asText(), number,
                repository.path("full_name").asText(), pull.path("title").asText(), text(pull, "body"),
                pull.path("html_url").asText(), pull.path("head").path("ref").asText(),
                pull.path("base").path("ref").asText(), pull.path("head").path("sha").asText(), state, mergeableState,
                pull.path("draft").asBoolean(), pull.path("user").path("id").asLong(),
                pull.path("user").path("login").asText(), ids(pull.path("assignees")), logins(pull.path("assignees")),
                ids(pull.path("requested_reviewers")), logins(pull.path("requested_reviewers")),
                names(pull.path("labels")), instant(pull, "created_at"), merged ? instant(pull, "merged_at") : 0,
                instant(pull, "closed_at"), "dirty".equals(mergeableState), true, instant(pull, "updated_at"),
                clock.millis());
    }

    private PublishedComment issueComment(final JsonNode comment) {
        final var id = comment.path("id").asLong();
        return new PublishedComment(comment.path("node_id").asText(), id, null, "issue:" + id,
                comment.path("user").path("id").asLong(), comment.path("user").path("login").asText(),
                comment.path("body").asText(), false, false, true, "published", instant(comment, "created_at"),
                instant(comment, "updated_at"), null, List.of());
    }

    private PublishedComment reviewComment(final JsonNode comment, final String threadId, final String state,
                                           final Long draftId) {
        final var line = comment.path("line").isNumber() ? comment.path("line").asLong() : null;
        final var left = "LEFT".equals(comment.path("side").asText());
        final var path = comment.path("path").asText(null);
        final var position = path == null ? null : new DiscussionPosition(null, null,
                comment.path("commit_id").asText(null), path, path, left ? line : null, left ? null : line, null);
        return new PublishedComment(comment.path("node_id").asText(), comment.path("id").asLong(), draftId,
                threadId == null ? "comment:" + comment.path("id").asLong() : threadId,
                comment.path("user").path("id").asLong(), comment.path("user").path("login").asText(),
                comment.path("body").asText(), threadId != null, false, false, state, instant(comment, "created_at"),
                instant(comment, "updated_at"), position, List.of());
    }

    private PublishedComment threadComment(final JsonNode thread, final JsonNode comment) {
        final var left = "LEFT".equals(thread.path("diffSide").asText());
        final var lineNode = thread.path("line").isNumber() ? thread.path("line") : thread.path("originalLine");
        final Long line = lineNode.isNumber() ? lineNode.asLong() : null;
        final var path = thread.path("path").asText();
        return new PublishedComment(thread.path("id").asText() + ":" + comment.path("databaseId").asLong(),
                comment.path("databaseId").asLong(), null, thread.path("id").asText(),
                comment.path("author").path("databaseId").isNumber()
                        ? comment.path("author").path("databaseId").asLong() : null,
                comment.path("author").path("login").asText(null), comment.path("body").asText(), true,
                thread.path("isResolved").asBoolean(), false, "published", instant(comment, "createdAt"),
                instant(comment, "updatedAt"),
                new DiscussionPosition(null, null, null, path, path, left ? line : null, left ? null : line, null),
                List.of());
    }

    private static Map<String, Object> lineComment(final DiscussionPosition position, final String body,
                                                   final boolean includeCommit) {
        final var comment = new LinkedHashMap<String, Object>();
        comment.put("body", body);
        if (includeCommit && position.headSha() != null) {
            comment.put("commit_id", position.headSha());
        }
        final var right = position.newLine() != null;
        comment.put("path", right ? position.newPath() : position.oldPath());
        comment.put("line", right ? position.newLine() : position.oldLine());
        comment.put("side", right ? "RIGHT" : "LEFT");
        return comment;
    }

    private static ReviewerStatusResponse reviewer(final JsonNode user, final String status) {
        final var login = user.path("login").asText();
        return new ReviewerStatusResponse(user.path("id").asLong(), login, login, text(user, "avatar_url"), status);
    }

    private static List<Long> ids(final JsonNode users) {
        final var ids = new ArrayList<Long>();
        users.forEach(user -> ids.add(user.path("id").asLong()));
        return ids;
    }

    private static List<String> logins(final JsonNode users) {
        final var logins = new ArrayList<String>();
        users.forEach(user -> logins.add(user.path("login").asText()));
        return logins;
    }

    private static List<String> names(final JsonNode labels) {
        final var names = new ArrayList<String>();
        labels.forEach(label -> names.add(label.path("name").asText()));
        return names;
    }

    private static long instant(final JsonNode node, final String field) {
        final var value = node.path(field).asText(null);
        return value == null || value.isBlank() ? 0 : Instant.parse(value).toEpochMilli();
    }

    private static String text(final JsonNode node, final String field) {
        final var value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    // ---------------------------------------------------------------- Transport

    String repository(final long repositoryId, final String token) {
        return repositoryNames.computeIfAbsent(repositoryId,
                id -> get("/repositories/" + id, token).path("full_name").asText());
    }

    private JsonNode pull(final long repositoryId, final long number, final String token) {
        return get(pullPath(repositoryId, number, token), token);
    }

    private String pullPath(final long repositoryId, final long number, final String token) {
        return "/repos/" + repository(repositoryId, token) + "/pulls/" + number;
    }

    private String issuePath(final long repositoryId, final long number, final String token) {
        return "/repos/" + repository(repositoryId, token) + "/issues/" + number;
    }

    private JsonNode graphql(final String query, final Map<String, Object> variables, final String token) {
        final var response = send("POST", URI.create(graphqlUrl), token, Map.of("query", query, "variables", variables));
        if (response.has("errors") && response.path("errors").size() > 0) {
            throw ArnoldError.raise(ArnoldErrorCode.CLIENT_FAILURE)
                    .message("GitHub GraphQL request failed: " + response.path("errors").get(0).path("message").asText())
                    .build();
        }
        return response.path("data");
    }

    private JsonNode get(final String path, final String token) {
        return json(base(new Request.Builder().url(apiUrl + path), token).get().build());
    }

    private JsonNode send(final String method, final String path, final String token, final Object body) {
        return send(method, URI.create(apiUrl + path), token, body);
    }

    private JsonNode send(final String method, final URI uri, final String token, final Object body) {
        try {
            final var requestBody = body == null ? (method.equals("DELETE") ? null : RequestBody.create(new byte[0]))
                    : RequestBody.create(mapper.writeValueAsBytes(body), JSON);
            return json(base(new Request.Builder().url(uri.toString()), token).method(method, requestBody).build());
        } catch (IOException e) {
            throw failure(e);
        }
    }

    private String text(final Request.Builder builder, final String token, final String accept) {
        try (var response = execute(base(builder, token).header("Accept", accept).get().build())) {
            return response.body() == null ? "" : response.body().string();
        } catch (IOException e) {
            throw failure(e);
        }
    }

    private JsonNode json(final Request request) {
        try (var response = execute(request)) {
            final var body = response.body() == null ? "" : response.body().string();
            return body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
        } catch (IOException e) {
            throw failure(e);
        }
    }

    private Request.Builder base(final Request.Builder builder, final String token) {
        builder.header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", API_VERSION);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private Response execute(final Request request) throws IOException {
        for (var attempt = 0; ; attempt++) {
            final var response = http.newCall(request).execute();
            final var retryable = response.code() >= 500 || response.code() == 429
                    || response.code() == 403 && "0".equals(response.header("X-RateLimit-Remaining"));
            if (!retryable || attempt == 2) {
                ensureSuccess(response);
                return response;
            }
            response.close();
            try {
                Thread.sleep(250L * (attempt + 1));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while retrying GitHub", interrupted);
            }
        }
    }

    private static void ensureSuccess(final Response response) throws IOException {
        if (response.isSuccessful()) {
            return;
        }
        final var code = response.code();
        final var body = response.body() == null ? "" : response.body().string();
        response.close();
        final var message = "GitHub request failed with HTTP " + code + (body.isBlank() ? "" : ": "
                + body.substring(0, Math.min(body.length(), 500)));
        throw ArnoldError.raise(code == 401 ? ArnoldErrorCode.AUTH_ERROR
                        : code == 404 ? ArnoldErrorCode.NOT_FOUND_ERROR : ArnoldErrorCode.CLIENT_FAILURE)
                .context(Map.of("httpStatus", code)).message(message).build();
    }

    private static RuntimeException failure(final IOException e) {
        return ArnoldError.propagate(e).errorCode(ArnoldErrorCode.CLIENT_FAILURE).message("Failed to call GitHub").build();
    }

    private static String encodePath(final String path) {
        final var parts = path.split("/");
        final var encoded = new StringBuilder();
        for (var i = 0; i < parts.length; i++) {
            if (i > 0) {
                encoded.append('/');
            }
            encoded.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return encoded.toString();
    }
}
