package com.phonepe.devx.arnold.core.provider.gitlab;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.devx.arnold.core.common.ArnoldCommentMarker;
import com.phonepe.devx.arnold.core.config.GitLabConfiguration;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.devx.arnold.core.model.vcs.PublishedComment;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.devx.arnold.models.comment.DiscussionPosition;
import com.phonepe.devx.arnold.models.comment.DiscussionLineRange;
import com.phonepe.devx.arnold.models.comment.SuggestionResponse;
import com.phonepe.devx.arnold.models.comment.PublishReviewRequest;
import com.phonepe.devx.arnold.models.comment.ResolveDiscussionRequest;
import com.phonepe.devx.arnold.models.comment.UpdateDraftRequest;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestFilesResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestDiffVersionResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestReviewResponse;
import com.phonepe.devx.arnold.models.mergerequest.ReviewerStatusResponse;
import com.phonepe.devx.arnold.models.mergerequest.ReviewerCandidateResponse;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Singleton
public class GitLabClientImpl implements GitLabClient {
    private static final MediaType JSON = MediaType.get("application/json");
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    @SuppressWarnings("java:S1075")
    private static final String PROJECTS_PATH = "/projects/";
    @SuppressWarnings("java:S1075")
    private static final String MERGE_REQUESTS_PATH = "/merge_requests/";
    private static final String GITLAB_CALL_FAILED = "Failed to call GitLab";
    private static final String APPROVED = "approved";
    private static final String REQUESTED_CHANGES = "requested_changes";
    @SuppressWarnings("java:S1075")
    private static final String MERGE_REQUEST_PATH = "/-" + MERGE_REQUESTS_PATH;

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
    public MergeRequestResponse addReviewer(final long projectId, final long mergeRequestIid, final String username,
                                            final String accessToken) {
        final var normalized = username.startsWith("@") ? username.substring(1) : username;
        final var url = apiRequest("/users", accessToken).build().url().newBuilder()
                .addQueryParameter("username", normalized).build();
        final var reviewer = Arrays.stream(execute(new Request.Builder().url(url)
                        .header(AUTHORIZATION, BEARER_PREFIX + accessToken).get().build(), UserResponse[].class))
                .filter(user -> normalized.equalsIgnoreCase(user.username()))
                .findFirst()
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR)
                        .message("GitLab reviewer not found").build());
        final var mergeRequest = execute(apiRequest(PROJECTS_PATH + projectId + MERGE_REQUESTS_PATH + mergeRequestIid,
                        accessToken)
                .get().build(), MergeRequest.class);
        final var reviewerIds = new LinkedHashSet<>(ids(mergeRequest.reviewers()));
        reviewerIds.add(reviewer.id());
        return updateReviewers(projectId, mergeRequestIid, reviewerIds, accessToken);
    }

    @Override
    public MergeRequestResponse removeReviewer(final long projectId, final long mergeRequestIid, final long reviewerId,
                                               final String accessToken) {
        final var mergeRequest = execute(apiRequest(PROJECTS_PATH + projectId + MERGE_REQUESTS_PATH + mergeRequestIid,
                        accessToken)
                .get().build(), MergeRequest.class);
        final var reviewerIds = new LinkedHashSet<>(ids(mergeRequest.reviewers()));
        reviewerIds.remove(reviewerId);
        return updateReviewers(projectId, mergeRequestIid, reviewerIds, accessToken);
    }

    @Override
    public List<ReviewerCandidateResponse> searchReviewers(final long projectId, final String query,
                                                           final String accessToken) {
        final var url = apiRequest(PROJECTS_PATH + projectId + "/members/all", accessToken).build().url().newBuilder()
                .addQueryParameter("query", query)
                .addQueryParameter("per_page", "20")
                .build();
        return Arrays.stream(execute(new Request.Builder().url(url)
                        .header(AUTHORIZATION, BEARER_PREFIX + accessToken).get().build(), UserResponse[].class))
                .map(user -> new ReviewerCandidateResponse(user.id(),
                        user.name() == null || user.name().isBlank() ? user.username() : user.name(),
                        user.username(), user.avatarUrl()))
                .toList();
    }

    @Override
    public void triggerSmartDiff(final String mergeRequestUrl, final long projectId, final long mergeRequestIid,
                                 final String headSha, final String executionId) {
        final var clio = configuration.clio();
        if (clio == null) {
            throw clientFailure("Clio pipeline is not configured");
        }
        final var body = new FormBody.Builder()
                .add("token", clio.triggerToken())
                .add("ref", clio.ref())
                .add("variables[WORKFLOW]", "smart_diff")
                .add("variables[MR_URL]", mergeRequestUrl)
                .add("variables[PROJECT_ID]", Long.toString(projectId))
                .add("variables[MR_IID]", Long.toString(mergeRequestIid))
                .add("variables[EXPECTED_HEAD_SHA]", headSha)
                .add("variables[EXECUTION_ID]", executionId)
                .build();
        executeDiscard(new Request.Builder().url(endpoint("/api/v4/projects/" + clio.projectId()
                + "/trigger/pipeline")).post(body).build());
    }

    @Override
    public ChangeRequestRecord getMergeRequest(final long projectId, final long mergeRequestIid,
                                               final String accessToken) {
        return getMergeRequest(Long.toString(projectId), mergeRequestIid, accessToken);
    }

    @Override
    public boolean isProjectArchived(final long projectId, final String accessToken) {
        final var project = execute(apiRequest(PROJECTS_PATH + projectId, accessToken).get().build(),
                com.fasterxml.jackson.databind.JsonNode.class);
        if (!project.path("archived").isBoolean()) {
            throw clientFailure("GitLab returned a project without archived status");
        }
        return project.get("archived").booleanValue();
    }

    @Override
    public List<ChangeRequestRecord> listMergeRequests(final long providerUserId,
                                                       final MergeRequestRole role,
                                                       final String state,
                                                       final Long updatedAfter,
                                                       final String accessToken) {
        final var url = apiRequest("/merge_requests", accessToken).build().url().newBuilder()
                .addQueryParameter("scope", "all")
                .addQueryParameter("non_archived", "true")
                .addQueryParameter("state", state)
                .addQueryParameter("per_page", "100")
                .addQueryParameter("order_by", "updated_at")
                .addQueryParameter("sort", "asc")
                .addQueryParameter(roleParameter(role), Long.toString(providerUserId));
        if (updatedAfter != null && updatedAfter > 0) {
            url.addQueryParameter("updated_after", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(updatedAfter)));
        }
        var request = new Request.Builder().url(url.build())
                .header(AUTHORIZATION, BEARER_PREFIX + accessToken).get().build();
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
        final var request = apiRequest(mergeRequestPath(projectId, mergeRequestIid) + "/discussions", accessToken)
                .post(RequestBody.create(writeJson(new DiscussionRequest(body, null)), JSON)).build();
        final var discussion = execute(request, DiscussionResponse.class);
        if (discussion.notes() == null || discussion.notes().isEmpty()) {
            throw clientFailure("GitLab returned a discussion without a note");
        }
        return toComment(discussion.id(), discussion.notes().get(0), discussion.individualNote());
    }

    @Override
    public MergeRequestReviewResponse getMergeRequestReview(final long projectId, final long mergeRequestIid,
                                                            final String accessToken) {
        final var path = mergeRequestPath(projectId, mergeRequestIid);
        final var mergeRequest = execute(apiRequest(path, accessToken).get().build(), MergeRequest.class);
        final var approvals = execute(apiRequest(path + "/approvals", accessToken).get().build(), ApprovalResponse.class);
        final var approvedIds = new HashSet<Long>();
        list(approvals.approvedBy()).stream().map(ApprovedByResponse::user).filter(java.util.Objects::nonNull)
                .map(UserResponse::id).forEach(approvedIds::add);
        final var assignedReviewers = list(mergeRequest.reviewers());
        final var reviewers = assignedReviewers.stream().map(reviewer -> new ReviewerStatusResponse(
                reviewer.id(), name(reviewer), reviewer.username(), reviewer.avatarUrl(),
                reviewerState(approvedIds.contains(reviewer.id()), mergeRequest.detailedMergeStatus(),
                        assignedReviewers.size()))).toList();
        return new MergeRequestReviewResponse(mergeRequest.detailedMergeStatus(), mergeRequest.hasConflicts(),
                mergeRequest.blockingDiscussionsResolved(), mergeRequest.headPipeline() == null ? null
                : mergeRequest.headPipeline().status(), mergeRequest.headPipeline() == null ? null
                : mergeRequest.headPipeline().webUrl(), value(approvals.approvalsRequired()),
                value(approvals.approvalsLeft()), approvals.approved(), reviewers);
    }

    @Override
    public String getMergeRequestDiff(final long projectId, final long mergeRequestIid, final String accessToken) {
        final var path = PROJECTS_PATH + projectId + MERGE_REQUESTS_PATH + mergeRequestIid + "/raw_diffs";
        return executeText(apiRequest(path, accessToken).get().build());
    }

    @Override
    public String getMergeRequestDiffVersion(final long projectId, final long mergeRequestIid, final long versionId,
                                             final String accessToken) {
        final var version = execute(apiRequest(mergeRequestPath(projectId, mergeRequestIid) + "/versions/" + versionId,
                accessToken).get().build(), DiffVersionDetailsResponse.class);
        return toPatch(version.diffs());
    }

    @Override
    public List<MergeRequestDiffVersionResponse> listMergeRequestDiffVersions(final long projectId,
                                                                              final long mergeRequestIid,
                                                                              final String accessToken) {
        final var path = mergeRequestPath(projectId, mergeRequestIid);
        final var versions = execute(apiRequest(path + "/versions?per_page=100", accessToken).get().build(),
                DiffVersionResponse[].class);
        final var titles = new HashMap<String, String>();
        Arrays.stream(execute(apiRequest(path + "/commits?per_page=100", accessToken).get().build(),
                CommitResponse[].class)).forEach(commit -> titles.put(commit.id(), commit.title()));
        return Arrays.stream(versions)
                .map(version -> new MergeRequestDiffVersionResponse(version.id(), version.baseCommitSha(),
                        version.startCommitSha(), version.headCommitSha(), timestamp(version.createdAt()),
                        titles.get(version.headCommitSha())))
                .toList();
    }

    @Override
    public MergeRequestFilesResponse getMergeRequestFiles(final long projectId, final long mergeRequestIid,
                                                          final String oldPath, final String newPath,
                                                          final String baseSha, final String headSha,
                                                          final String accessToken) {
        final var version = baseSha == null || headSha == null ? latestVersion(projectId, mergeRequestIid, accessToken)
                : null;
        return new MergeRequestFilesResponse(getFile(projectId, oldPath,
                version == null ? baseSha : version.baseCommitSha(), accessToken), getFile(projectId, newPath,
                version == null ? headSha : version.headCommitSha(), accessToken));
    }

    @Override
    public PublishedComment createDiscussion(final CreateDiscussionRequest create, final String accessToken) {
        final var mergeRequestPath = mergeRequestPath(create.getProjectId(), create.getMrIid());
        if (create.getDiscussionId() != null) {
            final var request = apiRequest(mergeRequestPath + "/discussions/"
                    + encodePathSegment(create.getDiscussionId()) + "/notes", accessToken)
                    .post(RequestBody.create(writeJson(new DiscussionRequest(create.getBody(), null)), JSON)).build();
            return toComment(create.getDiscussionId(), execute(request, NoteResponse.class), false);
        }
        if (create.getPosition() == null) {
            final var request = apiRequest(mergeRequestPath + "/notes", accessToken)
                    .post(RequestBody.create(writeJson(new DiscussionRequest(create.getBody(), null)), JSON)).build();
            return toComment(null, execute(request, NoteResponse.class), true);
        }
        final var position = completePosition(create.getProjectId(), create.getMrIid(), create.getPosition(), accessToken);
        final var request = apiRequest(mergeRequestPath + "/discussions", accessToken)
                .post(RequestBody.create(writeJson(new DiscussionRequest(create.getBody(), position)), JSON)).build();
        final var discussion = execute(request, DiscussionResponse.class);
        if (discussion.notes() == null || discussion.notes().isEmpty()) {
            throw clientFailure("GitLab returned a discussion without a note");
        }
        final var note = discussion.notes().get(0);
        return toComment(discussion.id(), note, discussion.individualNote());
    }

    @Override
    public List<PublishedComment> listDiscussions(final long projectId, final long mergeRequestIid,
                                                  final String accessToken) {
        final var comments = new ArrayList<PublishedComment>();
        for (var page = 1; ; page++) {
            final var discussions = execute(apiRequest(mergeRequestPath(projectId, mergeRequestIid)
                    + "/discussions?per_page=100&page=" + page, accessToken).get().build(), DiscussionResponse[].class);
            Arrays.stream(discussions).forEach(discussion -> list(discussion.notes()).stream()
                    .map(note -> toComment(discussion.id(), note,
                            discussion.individualNote())).forEach(comments::add));
            if (discussions.length < 100) {
                return List.copyOf(comments);
            }
        }
    }

    @Override
    public List<PublishedComment> listDrafts(final long projectId, final long mergeRequestIid,
                                             final String accessToken, final String authorUsername) {
        final var drafts = new ArrayList<PublishedComment>();
        for (var page = 1; ; page++) {
            final var response = execute(apiRequest(mergeRequestPath(projectId, mergeRequestIid)
                    + "/draft_notes?per_page=100&page=" + page, accessToken).get().build(), DraftNoteResponse[].class);
            Arrays.stream(response).map(draft -> toDraft(draft, authorUsername)).forEach(drafts::add);
            if (response.length < 100) {
                return List.copyOf(drafts);
            }
        }
    }

    @Override
    public PublishedComment createDraft(final CreateDiscussionRequest create, final String accessToken,
                                        final String authorUsername) {
        final var position = draftPosition(create.getProjectId(), create.getMrIid(), create.getPosition(), accessToken);
        final var body = new DraftRequest(create.getBody(), create.getDiscussionId(), position);
        final var request = apiRequest(mergeRequestPath(create.getProjectId(), create.getMrIid()) + "/draft_notes",
                accessToken).post(RequestBody.create(writeJson(body), JSON)).build();
        return toDraft(execute(request, DraftNoteResponse.class), authorUsername);
    }

    @Override
    public PublishedComment updateDraft(final long draftId, final UpdateDraftRequest update, final String accessToken,
                                        final String authorUsername) {
        final var request = apiRequest(mergeRequestPath(update.getProjectId(), update.getMrIid()) + "/draft_notes/" + draftId,
                accessToken).put(RequestBody.create(writeJson(new DraftUpdateRequest(update.getBody())), JSON)).build();
        return toDraft(execute(request, DraftNoteResponse.class), authorUsername);
    }

    @Override
    public void deleteDraft(final long draftId, final long projectId, final long mergeRequestIid,
                            final String accessToken) {
        executeDiscard(apiRequest(mergeRequestPath(projectId, mergeRequestIid) + "/draft_notes/" + draftId,
                accessToken).delete().build());
    }

    @Override
    public void publishDrafts(final PublishReviewRequest publish, final String accessToken) {
        final var approve = APPROVED.equals(publish.getReviewerState());
        final var body = new PublishDraftsRequest(blankToNull(publish.getSummary()),
                approve ? "reviewed" : publish.getReviewerState());
        executeDiscard(apiRequest(mergeRequestPath(publish.getProjectId(), publish.getMrIid())
                + "/draft_notes/bulk_publish", accessToken)
                .post(RequestBody.create(writeJson(body), JSON)).build());
        if (approve) {
            executeDiscard(apiRequest(mergeRequestPath(publish.getProjectId(), publish.getMrIid()) + "/approve",
                    accessToken).post(RequestBody.create("{}", JSON)).build());
        }
    }

    @Override
    public void resolveDiscussion(final String discussionId, final ResolveDiscussionRequest resolve,
                                  final String accessToken) {
        executeDiscard(apiRequest(mergeRequestPath(resolve.getProjectId(), resolve.getMrIid()) + "/discussions/"
                + encodePathSegment(discussionId), accessToken)
                .put(RequestBody.create(writeJson(new ResolveRequest(resolve.isResolved())), JSON)).build());
    }

    @Override
    public void applySuggestion(final long suggestionId, final String commitMessage, final String accessToken) {
        executeDiscard(apiRequest("/suggestions/" + suggestionId + "/apply", accessToken)
                .put(RequestBody.create(writeJson(new ApplySuggestionBody(blankToNull(commitMessage))), JSON)).build());
    }

    private String mergeRequestPath(final long projectId, final long mergeRequestIid) {
        return PROJECTS_PATH + projectId + MERGE_REQUESTS_PATH + mergeRequestIid;
    }

    private PositionRequest completePosition(final long projectId, final long mergeRequestIid,
                                             final DiscussionPosition requested, final String accessToken) {
        if (requested == null) {
            return null;
        }
        final var latest = requested.baseSha() == null || requested.startSha() == null || requested.headSha() == null
                ? latestVersion(projectId, mergeRequestIid, accessToken) : null;
        return new PositionRequest(latest == null ? requested.baseSha() : latest.baseCommitSha(),
                latest == null ? requested.startSha() : latest.startCommitSha(),
                latest == null ? requested.headSha() : latest.headCommitSha(),
                requested.oldPath(), requested.newPath(), requested.oldLine() == null && requested.newLine() == null
                ? "file" : "text", requested.oldLine(), requested.newLine(),
                toLineRange(requested));
    }

    private PositionRequest draftPosition(final long projectId, final long mergeRequestIid,
                                           final DiscussionPosition requested, final String accessToken) {
        final var position = completePosition(projectId, mergeRequestIid, requested, accessToken);
        if (position == null || position.lineRange() == null) {
            return position;
        }
        final var range = position.lineRange();
        return new PositionRequest(position.baseSha(), position.startSha(), position.headSha(), position.oldPath(),
                position.newPath(), position.positionType(), position.oldLine(), position.newLine(),
                new LineRangeRequest(withoutLineNumbers(range.start()), withoutLineNumbers(range.end())));
    }

    private LineRangePointRequest withoutLineNumbers(final LineRangePointRequest point) {
        return new LineRangePointRequest(point.lineCode(), point.type(), null, null);
    }

    private DiffVersionResponse latestVersion(final long projectId, final long mergeRequestIid,
                                              final String accessToken) {
        final var versions = execute(apiRequest(mergeRequestPath(projectId, mergeRequestIid) + "/versions?per_page=1",
                accessToken).get().build(), DiffVersionResponse[].class);
        if (versions.length == 0) {
            throw clientFailure("GitLab returned no merge request diff version");
        }
        return versions[0];
    }

    private String getFile(final long projectId, final String path, final String ref, final String accessToken) {
        return executeText(apiRequest(PROJECTS_PATH + projectId + "/repository/files/" + encodePathSegment(path)
                + "/raw?ref=" + encodePathSegment(ref), accessToken).get().build());
    }

    private LineRangeRequest toLineRange(final DiscussionPosition position) {
        final var range = position.lineRange();
        if (range == null) {
            return null;
        }
        final var pathHash = sha1("old".equals(range.side()) ? position.oldPath() : position.newPath());
        return new LineRangeRequest(toLineRangePoint(pathHash, range.side(), range.startOldLine(), range.startNewLine()),
                toLineRangePoint(pathHash, range.side(), range.endOldLine(), range.endNewLine()));
    }

    private LineRangePointRequest toLineRangePoint(final String pathHash, final String side, final Long oldLine,
                                                   final Long newLine) {
        return new LineRangePointRequest(pathHash + "_" + (oldLine == null ? "" : oldLine) + "_"
                + (newLine == null ? "" : newLine), side, oldLine, newLine);
    }

    private static String reviewerState(final boolean approved, final String mergeStatus, final int reviewerCount) {
        if (approved) {
            return APPROVED;
        }
        return REQUESTED_CHANGES.equals(mergeStatus) && reviewerCount == 1 ? REQUESTED_CHANGES : "awaiting_review";
    }

    @SuppressWarnings("java:S4790")
    private String sha1(final String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw clientFailure("SHA-1 is unavailable");
        }
    }

    private String toPatch(final List<DiffResponse> diffs) {
        final var patch = new StringBuilder();
        for (final var diff : list(diffs)) {
            patch.append("diff --git a/").append(diff.oldPath()).append(" b/").append(diff.newPath()).append('\n');
            appendFileMode(patch, diff);
            patch.append("--- ").append(diff.newFile() ? "/dev/null" : "a/" + diff.oldPath()).append('\n')
                    .append("+++ ").append(diff.deletedFile() ? "/dev/null" : "b/" + diff.newPath()).append('\n');
            if (diff.diff() != null && !diff.diff().isBlank()) {
                patch.append(diff.diff()).append(diff.diff().endsWith("\n") ? "" : "\n");
            }
        }
        return patch.toString();
    }

    private static void appendFileMode(final StringBuilder patch, final DiffResponse diff) {
        if (diff.newFile()) {
            patch.append("new file mode ").append(diff.newMode()).append('\n');
        } else if (diff.deletedFile()) {
            patch.append("deleted file mode ").append(diff.oldMode()).append('\n');
        } else if (diff.renamedFile()) {
            patch.append("similarity index ").append(diff.diff() == null || diff.diff().isBlank() ? "100" : "99")
                    .append("%\nrename from ").append(diff.oldPath()).append("\nrename to ").append(diff.newPath())
                    .append('\n');
        }
    }

    private PublishedComment toComment(final String discussionId, final NoteResponse note,
                                       final boolean individualNote) {
        final var updatedAt = timestamp(note.updatedAt(), System.currentTimeMillis());
        return new PublishedComment(Long.toString(note.id()), note.id(), null, discussionId, id(note.author()),
                username(note.author()), ArnoldCommentMarker.strip(note.body()), note.resolvable(), Boolean.TRUE.equals(note.resolved()),
                individualNote || note.system(), note.system() ? "system" : "published",
                timestamp(note.createdAt(), updatedAt), updatedAt,
                toPosition(note.position()), toSuggestions(note.suggestions()),
                ArnoldCommentMarker.persona(note.body()).orElse(null));
    }

    private PublishedComment toDraft(final DraftNoteResponse draft, final String authorUsername) {
        final var updatedAt = timestamp(draft.updatedAt(), System.currentTimeMillis());
        return new PublishedComment("draft:" + draft.id(), draft.id(), draft.id(),
                draft.discussionId() == null ? draft.inReplyToDiscussionId() : draft.discussionId(),
                draft.author() == null ? draft.authorId() : id(draft.author()),
                draft.author() == null ? authorUsername : username(draft.author()), ArnoldCommentMarker.strip(draft.note()), false, false,
                draft.discussionId() == null && draft.inReplyToDiscussionId() == null && draft.position() == null,
                "draft", timestamp(draft.createdAt(), updatedAt), updatedAt, toPosition(draft.position()), List.of());
    }

    private static DiscussionPosition toPosition(final PositionResponse position) {
        return position == null ? null : new DiscussionPosition(position.baseSha(), position.startSha(), position.headSha(),
                position.oldPath(), position.newPath(), position.oldLine(), position.newLine(),
                toLineRange(position.lineRange()));
    }

    private static DiscussionLineRange toLineRange(final LineRangeResponse range) {
        return range == null ? null : new DiscussionLineRange(range.start().type(), range.start().oldLine(),
                range.start().newLine(), range.end().oldLine(), range.end().newLine());
    }

    private static List<SuggestionResponse> toSuggestions(final List<SuggestionApiResponse> suggestions) {
        return list(suggestions).stream().map(suggestion -> new SuggestionResponse(suggestion.id(),
                suggestion.applicable(), suggestion.applied(), suggestion.fromContent(), suggestion.toContent(),
                suggestion.fromLine(), suggestion.toLine())).toList();
    }

    private Request.Builder apiRequest(final String path, final String accessToken) {
        return new Request.Builder().url(endpoint("/api/v4" + path)).header(AUTHORIZATION, BEARER_PREFIX + accessToken);
    }

    private ChangeRequestRecord getMergeRequest(final String projectReference, final long mergeRequestIid,
                                                final String accessToken) {
        final var path = PROJECTS_PATH + encodePathSegment(projectReference)
                + MERGE_REQUESTS_PATH + mergeRequestIid;
        final var mergeRequest = execute(apiRequest(path, accessToken).get().build(), MergeRequest.class);
        return toRecord(mergeRequest, projectPath(mergeRequest.webUrl()), System.currentTimeMillis());
    }

    private MergeRequestResponse toResponse(final ChangeRequestRecord record) {
        return new MergeRequestResponse(record.id(), record.provider().name(), record.repositoryId(),
                record.providerId(), record.number(), record.repositoryPath(), record.title(),
                record.description(), record.webUrl(), record.sourceBranch(), record.targetBranch(), record.headSha(),
                record.state(), record.mergeStatus(), record.draft(), record.authorId(), record.authorName(),
                record.assigneeIds(), record.assigneeNames(), record.reviewerIds(), record.reviewerNames(),
                record.labels(), record.createdAt(), record.mergedAt(), record.closedAt(),
                record.hasConflicts(), record.blockingDiscussionsResolved(), record.providerUpdatedAt(),
                record.lastSyncedAt());
    }

    private MergeRequestResponse updateReviewers(final long projectId, final long mergeRequestIid,
                                                 final java.util.Set<Long> reviewerIds, final String accessToken) {
        final var request = apiRequest(PROJECTS_PATH + projectId + MERGE_REQUESTS_PATH + mergeRequestIid, accessToken)
                .put(RequestBody.create(writeJson(new UpdateReviewersRequest(List.copyOf(reviewerIds))), JSON)).build();
        final var updated = execute(request, MergeRequest.class);
        return toResponse(toRecord(updated, projectPath(updated.webUrl()), System.currentTimeMillis()));
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
                name(mergeRequest.author()), ids(mergeRequest.assignees()), names(mergeRequest.assignees()),
                ids(mergeRequest.reviewers()), names(mergeRequest.reviewers()), List.of(), list(mergeRequest.labels()),
                optionalTimestamp(mergeRequest.createdAt()), optionalTimestamp(mergeRequest.mergedAt()),
                optionalTimestamp(mergeRequest.closedAt()), mergeRequest.hasConflicts(),
                !Boolean.FALSE.equals(mergeRequest.blockingDiscussionsResolved()),
                timestamp(mergeRequest.updatedAt()), syncedAt, false);
    }

    private MergeRequestPage executeMergeRequestPage(final Request request) {
        try (Response response = executeRequest(request)) {
            final List<MergeRequest> items = mapper.readValue(response.body().byteStream(),
                    mapper.getTypeFactory().constructCollectionType(List.class, MergeRequest.class));
            final var nextPage = response.header("X-Next-Page");
            return new MergeRequestPage(items, nextPage == null || nextPage.isBlank() ? null : nextPage);
        } catch (IOException e) {
            throw ArnoldError.propagate(e).errorCode(ArnoldErrorCode.CLIENT_FAILURE)
                    .message(GITLAB_CALL_FAILED).build();
        }
    }

    private String endpoint(final String path) {
        return configuration.baseUrl().replaceAll("/+$", "") + path;
    }

    private <T> T execute(final Request request, final Class<T> responseType) {
        try (Response response = executeRequest(request)) {
            return mapper.readValue(response.body().byteStream(), responseType);
        } catch (IOException e) {
            throw ArnoldError.propagate(e).errorCode(ArnoldErrorCode.CLIENT_FAILURE)
                    .message(GITLAB_CALL_FAILED).build();
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
            if (code != 429 && code < 500) {
                final var responseBody = response.body().string();
                response.close();
                throw ArnoldError.raise(ArnoldErrorCode.CLIENT_FAILURE)
                        .context(Map.of("httpStatus", code))
                        .message("GitLab request failed with HTTP " + code
                                + (responseBody.isBlank() ? "" : ": " + responseBody)).build();
            }
            response.close();
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

    private String executeText(final Request request) {
        try (Response response = executeRequest(request)) {
            return response.body().string();
        } catch (IOException e) {
            throw ArnoldError.propagate(e).errorCode(ArnoldErrorCode.CLIENT_FAILURE)
                    .message(GITLAB_CALL_FAILED).build();
        }
    }

    private void executeDiscard(final Request request) {
        try (Response ignored = executeRequest(request)) {
            // the response body is intentionally not read
        } catch (IOException e) {
            throw ArnoldError.propagate(e).errorCode(ArnoldErrorCode.CLIENT_FAILURE)
                    .message(GITLAB_CALL_FAILED).build();
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
        return timestamp(value, 0);
    }

    private static long timestamp(final String value, final long fallback) {
        return value == null ? fallback : OffsetDateTime.parse(value).toInstant().toEpochMilli();
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

    private static String username(final UserResponse user) {
        return user == null ? null : user.username();
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static List<Long> ids(final List<UserResponse> users) {
        return users == null ? List.of() : users.stream().map(UserResponse::id).toList();
    }

    private static List<String> names(final List<UserResponse> users) {
        return users == null ? List.of() : users.stream().map(GitLabClientImpl::name).toList();
    }

    private static String name(final UserResponse user) {
        if (user == null) {
            return null;
        }
        return user.name() == null || user.name().isBlank() ? user.username() : user.name();
    }

    private static <T> List<T> list(final List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static int value(final Integer value) {
        return value == null ? 0 : value;
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
    private record UserResponse(long id, String username, String name,
                                @JsonProperty("avatar_url") String avatarUrl) {
    }

    private record UpdateReviewersRequest(@JsonProperty("reviewer_ids") List<Long> reviewerIds) {
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
                                @JsonProperty("head_pipeline") PipelineResponse headPipeline,
                                @JsonProperty("updated_at") String updatedAt) {
    }

    private record MergeRequestPage(List<MergeRequest> items, String nextPage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PipelineResponse(String status, @JsonProperty("web_url") String webUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApprovalResponse(@JsonProperty("approvals_required") Integer approvalsRequired,
                                    @JsonProperty("approvals_left") Integer approvalsLeft, boolean approved,
                                    @JsonProperty("approved_by") List<ApprovedByResponse> approvedBy) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApprovedByResponse(UserResponse user) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record DiscussionRequest(String body, PositionRequest position) {
        DiscussionRequest {
            body = ArnoldCommentMarker.tag(body);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DiscussionResponse(String id, @JsonProperty("individual_note") boolean individualNote,
                                      List<NoteResponse> notes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NoteResponse(long id, UserResponse author, String body, boolean system, boolean resolvable,
                                Boolean resolved, @JsonProperty("created_at") String createdAt,
                                @JsonProperty("updated_at") String updatedAt, PositionResponse position,
                                List<SuggestionApiResponse> suggestions) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record DraftRequest(String note, @JsonProperty("in_reply_to_discussion_id") String discussionId,
                                PositionRequest position) {
        DraftRequest {
            note = ArnoldCommentMarker.tag(note);
        }
    }

    private record DraftUpdateRequest(String note) {
        DraftUpdateRequest {
            note = ArnoldCommentMarker.tag(note);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record PublishDraftsRequest(String note, @JsonProperty("reviewer_state") String reviewerState) {
        PublishDraftsRequest {
            note = ArnoldCommentMarker.tag(note);
        }
    }

    private record ResolveRequest(boolean resolved) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ApplySuggestionBody(@JsonProperty("commit_message") String commitMessage) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record PositionRequest(@JsonProperty("base_sha") String baseSha,
                                   @JsonProperty("start_sha") String startSha,
                                   @JsonProperty("head_sha") String headSha,
                                   @JsonProperty("old_path") String oldPath,
                                   @JsonProperty("new_path") String newPath,
                                   @JsonProperty("position_type") String positionType,
                                   @JsonProperty("old_line") Long oldLine,
                                   @JsonProperty("new_line") Long newLine,
                                   @JsonProperty("line_range") LineRangeRequest lineRange) {
    }

    private record LineRangeRequest(LineRangePointRequest start, LineRangePointRequest end) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record LineRangePointRequest(@JsonProperty("line_code") String lineCode, String type,
                                         @JsonProperty("old_line") Long oldLine,
                                         @JsonProperty("new_line") Long newLine) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PositionResponse(@JsonProperty("base_sha") String baseSha,
                                    @JsonProperty("start_sha") String startSha,
                                    @JsonProperty("head_sha") String headSha,
                                    @JsonProperty("old_path") String oldPath,
                                    @JsonProperty("new_path") String newPath,
                                    @JsonProperty("old_line") Long oldLine,
                                    @JsonProperty("new_line") Long newLine,
                                    @JsonProperty("line_range") LineRangeResponse lineRange) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LineRangeResponse(LineRangePointResponse start, LineRangePointResponse end) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LineRangePointResponse(String type, @JsonProperty("old_line") Long oldLine,
                                          @JsonProperty("new_line") Long newLine) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DiffVersionResponse(long id, @JsonProperty("created_at") String createdAt,
                                       @JsonProperty("base_commit_sha") String baseCommitSha,
                                       @JsonProperty("start_commit_sha") String startCommitSha,
                                       @JsonProperty("head_commit_sha") String headCommitSha) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CommitResponse(String id, String title) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DiffVersionDetailsResponse(List<DiffResponse> diffs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DiffResponse(@JsonProperty("old_path") String oldPath,
                                @JsonProperty("new_path") String newPath,
                                @JsonProperty("a_mode") String oldMode,
                                @JsonProperty("b_mode") String newMode,
                                @JsonProperty("new_file") boolean newFile,
                                @JsonProperty("renamed_file") boolean renamedFile,
                                @JsonProperty("deleted_file") boolean deletedFile,
                                String diff) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SuggestionApiResponse(long id, boolean applicable, boolean applied,
                                         @JsonProperty("from_content") String fromContent,
                                         @JsonProperty("to_content") String toContent,
                                         @JsonProperty("from_line") int fromLine,
                                         @JsonProperty("to_line") int toLine) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DraftNoteResponse(long id, String note, UserResponse author,
                                     @JsonProperty("author_id") Long authorId,
                                     @JsonProperty("discussion_id") String discussionId,
                                     @JsonProperty("in_reply_to_discussion_id") String inReplyToDiscussionId,
                                     @JsonProperty("created_at") String createdAt,
                                     @JsonProperty("updated_at") String updatedAt,
                                     PositionResponse position) {
    }
}
