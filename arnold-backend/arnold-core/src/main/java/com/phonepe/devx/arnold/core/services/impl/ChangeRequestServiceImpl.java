package com.phonepe.devx.arnold.core.services.impl;
import com.phonepe.devx.arnold.core.services.PipelineTriggerClient;
import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.review.ReviewProgress;
import com.phonepe.devx.arnold.core.model.review.SmartDiffRecord;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.provider.VersionControlProviders;
import com.phonepe.devx.arnold.core.services.ChangeRequestService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeChangeRequestStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeReviewProgressStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeSmartDiffStore;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestFilesResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestDiffVersionResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestReviewResponse;
import com.phonepe.devx.arnold.models.mergerequest.ReviewerStatusResponse;
import com.phonepe.devx.arnold.models.mergerequest.ReviewerCandidateResponse;
import com.phonepe.devx.arnold.models.mergerequest.ViewedFilesRequest;
import com.phonepe.devx.arnold.models.mergerequest.ViewedFilesResponse;
import com.phonepe.devx.arnold.models.mergerequest.SmartDiffRequest;
import com.phonepe.devx.arnold.models.mergerequest.SmartDiffResponse;
import com.phonepe.devx.arnold.models.mergerequest.SmartDiffResultRequest;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class ChangeRequestServiceImpl implements ChangeRequestService {
    private static final int MAX_SYNC_ATTEMPTS = 8;
    private static final long SMART_DIFF_TIMEOUT_MS = 135 * 60 * 1000L;
    private static final String DISPATCH_ERROR = "Could not start Smart Diff generation. Please retry.";

    private final AerospikeChangeRequestStore mergeRequestStore;
    private final VersionControlProviders providers;
    private final AerospikeReviewProgressStore reviewProgressStore;
    private final AerospikeSmartDiffStore smartDiffStore;
    private final PipelineTriggerClient pipelines;

    @Inject
    public ChangeRequestServiceImpl(final AerospikeChangeRequestStore mergeRequestStore,
                                    final VersionControlProviders providers,
                                    final AerospikeReviewProgressStore reviewProgressStore,
                                    final AerospikeSmartDiffStore smartDiffStore,
                                    final PipelineTriggerClient pipelines) {
        this.mergeRequestStore = mergeRequestStore;
        this.providers = providers;
        this.reviewProgressStore = reviewProgressStore;
        this.smartDiffStore = smartDiffStore;
        this.pipelines = pipelines;
    }

    @Override
    public MergeRequestResponse get(final VersionControlProviderType provider,
                                    final String repositoryId,
                                    final long number,
                                    final String userId) {
        final var mergeRequest = mergeRequestStore.get(ChangeRequestRecord.key(provider,
                        repositoryId, number))
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
        if (provider == VersionControlProviderType.GITLAB && needsUserNames(mergeRequest)) {
            final var refreshed = providers.get(provider)
                    .resolveChangeRequest(mergeRequest.webUrl(), userId);
            synchronize(toRecord(refreshed));
            return refreshed;
        }
        return toResponse(mergeRequest);
    }

    @Override
    public MergeRequestResponse resolve(final VersionControlProviderType provider,
                                        final String url,
                                        final String userId) {
        final var mergeRequest = providers.get(provider).resolveChangeRequest(url, userId);
        synchronize(toRecord(mergeRequest));
        return mergeRequest;
    }

    @Override
    public MergeRequestResponse addReviewer(final long projectId, final long iid, final String username,
                                            final String userId) {
        return providers.get(VersionControlProviderType.GITLAB)
                .addChangeRequestReviewer(projectId, iid, username, userId);
    }

    @Override
    public MergeRequestResponse removeReviewer(final long projectId, final long iid, final long reviewerId,
                                               final String userId) {
        return providers.get(VersionControlProviderType.GITLAB)
                .removeChangeRequestReviewer(projectId, iid, reviewerId, userId);
    }

    @Override
    public List<ReviewerCandidateResponse> searchReviewers(final long projectId, final String query,
                                                           final String userId) {
        return providers.get(VersionControlProviderType.GITLAB)
                .searchChangeRequestReviewers(projectId, query, userId);
    }

    @Override
    public MergeRequestReviewResponse getReview(final long projectId, final long iid, final String userId) {
        final var review = providers.get(VersionControlProviderType.GITLAB)
                .getChangeRequestReview(projectId, iid, userId);
        final var stored = mergeRequestStore.get(ChangeRequestRecord.key(VersionControlProviderType.GITLAB,
                Long.toString(projectId), iid)).orElse(null);
        if (stored == null || stored.reviewerStates().size() != stored.reviewerIds().size()) {
            return review;
        }
        final var states = new HashMap<Long, String>();
        for (var index = 0; index < stored.reviewerIds().size(); index++) {
            states.put(stored.reviewerIds().get(index), reviewerState(stored.reviewerStates().get(index)));
        }
        return new MergeRequestReviewResponse(review.detailedMergeStatus(), review.hasConflicts(),
                review.discussionsResolved(), review.pipelineStatus(), review.pipelineUrl(), review.approvalsRequired(),
                review.approvalsLeft(), review.approved(), review.reviewers().stream().map(reviewer ->
                new ReviewerStatusResponse(reviewer.id(), reviewer.name(), reviewer.username(), reviewer.avatarUrl(),
                        "approved".equals(reviewer.status()) ? "approved"
                                : states.getOrDefault(reviewer.id(), reviewer.status()))).toList());
    }

    @Override
    public String getDiff(final long projectId, final long iid, final String userId) {
        return providers.get(VersionControlProviderType.GITLAB).getChangeRequestDiff(projectId, iid, userId);
    }

    @Override
    public String getDiffVersion(final long projectId, final long iid, final long versionId, final String userId) {
        return providers.get(VersionControlProviderType.GITLAB)
                .getChangeRequestDiffVersion(projectId, iid, versionId, userId);
    }

    @Override
    public List<MergeRequestDiffVersionResponse> listDiffVersions(final long projectId, final long iid,
                                                                  final String userId) {
        return providers.get(VersionControlProviderType.GITLAB).listChangeRequestDiffVersions(projectId, iid, userId);
    }

    @Override
    public MergeRequestFilesResponse getFiles(final long projectId, final long iid, final String oldPath,
                                              final String newPath, final String baseSha, final String headSha,
                                              final String userId) {
        return providers.get(VersionControlProviderType.GITLAB)
                .getChangeRequestFiles(projectId, iid, oldPath, newPath, baseSha, headSha, userId);
    }

    @Override
    public ViewedFilesResponse getViewedFiles(final long projectId, final long iid, final String headSha,
                                              final String userId) {
        return reviewProgressStore.get(ReviewProgress.key(userId, projectId, iid, headSha))
                .map(this::toResponse)
                .orElseGet(() -> new ViewedFilesResponse(headSha, List.of(), List.of(), Map.of(), 0));
    }

    @Override
    public ViewedFilesResponse updateViewedFiles(final long projectId, final long iid,
                                                 final ViewedFilesRequest request, final String userId) {
        final var id = ReviewProgress.key(userId, projectId, iid, request.getHeadSha());
        final var current = reviewProgressStore.get(id);
        final var storyStepIds = request.getStoryStepIds() == null
                ? current.map(ReviewProgress::storyStepIds).orElseGet(List::of)
                : request.getStoryStepIds();
        final var focusAreaFeedback = request.getFocusAreaFeedback() == null
                ? current.map(ReviewProgress::focusAreaFeedback).orElseGet(Map::of)
                : request.getFocusAreaFeedback();
        final var progress = new ReviewProgress(id, request.getHeadSha(), request.getFilePaths().stream().distinct().toList(),
                storyStepIds.stream().distinct().toList(), focusAreaFeedback, System.currentTimeMillis());
        if (current.isPresent()) {
            reviewProgressStore.update(progress);
        } else {
            reviewProgressStore.create(progress);
        }
        return toResponse(progress);
    }

    @Override
    public SmartDiffResponse getSmartDiff(final long projectId, final long iid, final String headSha) {
        return smartDiffStore.get(SmartDiffRecord.key(projectId, iid, headSha))
                .map(this::toResponse)
                .orElseGet(() -> new SmartDiffResponse(headSha, "not_requested", null, List.of(), 0, null,
                        "not_requested", 0));
    }

    @Override
    public SmartDiffResponse requestSmartDiff(final long projectId, final long iid, final SmartDiffRequest request,
                                              final String userId) {
        final var id = SmartDiffRecord.key(projectId, iid, request.getHeadSha());
        final var provider = providers.get(VersionControlProviderType.GITLAB);
        final var stored = mergeRequestStore.get(ChangeRequestRecord.key(VersionControlProviderType.GITLAB,
                        Long.toString(projectId), iid))
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
        final var live = provider.resolveChangeRequest(stored.webUrl(), userId);
        if (!Long.toString(projectId).equals(live.repositoryId()) || iid != live.number()
                || !request.getHeadSha().equalsIgnoreCase(live.headSha())) {
            throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        }
        synchronize(toRecord(live));
        for (var attempt = 0; attempt < MAX_SYNC_ATTEMPTS; attempt++) {
            final var current = smartDiffStore.getWithVersion(id);
            if (current.isEmpty()) {
                final var queued = queued(id, request.getHeadSha());
                try {
                    smartDiffStore.create(queued);
                } catch (RuntimeException createRace) {
                    continue;
                }
                return dispatch(live.webUrl(), projectId, iid, queued, userId);
            }
            final var value = current.get().value();
            final var regenerate = request.isRegenerate() && "completed".equals(value.status());
            if (!regenerate && !"failed".equals(value.status()) && !isStale(value)) {
                return toResponse(current.get().value());
            }
            final var queued = queued(id, request.getHeadSha());
            try {
                smartDiffStore.checkAndUpdate(queued, current.get().version());
                return dispatch(live.webUrl(), projectId, iid, queued, userId);
            } catch (ConcurrentUpdateException conflict) {
                continue;
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    @Override
    public SmartDiffResponse updateSmartDiff(final long projectId, final long iid,
                                             final SmartDiffResultRequest request) {
        final var id = SmartDiffRecord.key(projectId, iid, request.getHeadSha());
        for (var attempt = 0; attempt < MAX_SYNC_ATTEMPTS; attempt++) {
            final var current = smartDiffStore.getWithVersion(id)
                    .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
            if (!request.getExecutionId().equals(current.value().executionId())) {
                throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
            }
            final var status = current.value().status();
            if ("completed".equals(status) || "failed".equals(status)) {
                if (status.equals(request.getStatus())) {
                    return toResponse(current.value());
                }
                throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
            }
            if (status.equals(request.getStatus())) {
                return toResponse(current.value());
            }
            if (!("queued".equals(status) || "running".equals(status) && !"running".equals(request.getStatus()))) {
                throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
            }
            if ("completed".equals(request.getStatus())) {
                final var mergeRequest = mergeRequestStore.get(ChangeRequestRecord.key(VersionControlProviderType.GITLAB,
                                Long.toString(projectId), iid))
                        .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
                if (!request.getHeadSha().equalsIgnoreCase(mergeRequest.headSha())) {
                    throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
                }
            }
            final var updated = new SmartDiffRecord(id, request.getHeadSha(), request.getExecutionId(),
                    request.getStatus(), request.getSummary(), request.getGroups(), System.currentTimeMillis(),
                    request.getErrorMessage(),
                    request.getCommentAssessmentStatus() == null
                            ? current.value().commentAssessmentStatus() : request.getCommentAssessmentStatus(),
                    request.getAssessedCommentCount() == null
                            ? current.value().assessedCommentCount() : request.getAssessedCommentCount());
            try {
                smartDiffStore.checkAndUpdate(updated, current.version());
                return toResponse(updated);
            } catch (ConcurrentUpdateException conflict) {
                // Retry against the latest Aerospike generation.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    @Override
    public boolean synchronize(final ChangeRequestRecord mergeRequest) {
        for (var attempt = 0; attempt < MAX_SYNC_ATTEMPTS; attempt++) {
            final var current = mergeRequestStore.getWithVersion(mergeRequest.id());
            if (current.isEmpty()) {
                try {
                    mergeRequestStore.create(mergeRequest);
                    return true;
                } catch (RuntimeException createRace) {
                    continue;
                }
            }
            final var previous = current.get().value();
            // Older webhooks used receipt time for both unparsed GitLab dates.
            final var repairsWebhookDates = previous.createdAt() == previous.lastSyncedAt()
                    && previous.providerUpdatedAt() == previous.lastSyncedAt()
                    && mergeRequest.lastSyncedAt() > previous.lastSyncedAt()
                    && mergeRequest.createdAt() > 0 && mergeRequest.createdAt() < previous.createdAt();
            if (previous.providerUpdatedAt() > mergeRequest.providerUpdatedAt() && !repairsWebhookDates) {
                return false;
            }
            try {
                final var currentRecord = mergeRequest.reviewerStates().isEmpty() && !previous.reviewerStates().isEmpty()
                        ? mergeRequest.withReviewerStates(mergeRequest.reviewerIds().stream().map(id -> {
                            final var index = previous.reviewerIds().indexOf(id);
                            return index >= 0 && index < previous.reviewerStates().size()
                                    ? previous.reviewerStates().get(index) : "unreviewed";
                        }).toList()) : mergeRequest;
                mergeRequestStore.checkAndUpdate(currentRecord, current.get().version());
                return true;
            } catch (ConcurrentUpdateException conflict) {
                // Retry against the latest Aerospike generation.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.DB_ERROR).build();
    }

    @Override
    public Optional<ChangeRequestRecord> record(final String mergeRequestId) {
        return mergeRequestStore.get(mergeRequestId);
    }

    @Override
    public Map<String, ChangeRequestRecord> records(final Collection<String> mergeRequestIds) {
        return mergeRequestIds.isEmpty() ? Map.of() : mergeRequestStore.get(List.copyOf(mergeRequestIds));
    }

    @Override
    public MergeRequestResponse response(final ChangeRequestRecord mergeRequest) {
        return new MergeRequestResponse(mergeRequest.id(), mergeRequest.provider().name(), mergeRequest.repositoryId(),
                mergeRequest.providerId(), mergeRequest.number(), mergeRequest.repositoryPath(),
                mergeRequest.title(), mergeRequest.description(),
                mergeRequest.webUrl(), mergeRequest.sourceBranch(), mergeRequest.targetBranch(), mergeRequest.headSha(),
                mergeRequest.state(), mergeRequest.mergeStatus(), mergeRequest.draft(), mergeRequest.authorId(),
                mergeRequest.authorName(), mergeRequest.assigneeIds(), mergeRequest.assigneeNames(),
                mergeRequest.reviewerIds(), mergeRequest.reviewerNames(), mergeRequest.labels(),
                mergeRequest.createdAt(), mergeRequest.mergedAt(), mergeRequest.closedAt(),
                mergeRequest.hasConflicts(), mergeRequest.blockingDiscussionsResolved(),
                mergeRequest.providerUpdatedAt(), mergeRequest.lastSyncedAt());
    }

    private MergeRequestResponse toResponse(final ChangeRequestRecord mergeRequest) {
        return response(mergeRequest);
    }

    private ViewedFilesResponse toResponse(final ReviewProgress progress) {
        return new ViewedFilesResponse(progress.headSha(), progress.filePaths(), progress.storyStepIds(),
                progress.focusAreaFeedback(),
                progress.updatedAt());
    }

    private SmartDiffResponse toResponse(final SmartDiffRecord smartDiff) {
        return new SmartDiffResponse(smartDiff.headSha(), smartDiff.status(), smartDiff.summary(), smartDiff.groups(),
                smartDiff.updatedAt(), smartDiff.errorMessage(), smartDiff.commentAssessmentStatus(),
                smartDiff.assessedCommentCount());
    }

    private SmartDiffRecord queued(final String id, final String headSha) {
        return new SmartDiffRecord(id, headSha, UUID.randomUUID().toString(), "queued", null, List.of(),
                System.currentTimeMillis(), null, "queued", 0);
    }

    private boolean isStale(final SmartDiffRecord record) {
        return ("queued".equals(record.status()) || "running".equals(record.status()))
                && System.currentTimeMillis() - record.updatedAt() > SMART_DIFF_TIMEOUT_MS;
    }

    private SmartDiffResponse dispatch(final String mergeRequestUrl, final long projectId, final long iid,
                                       final SmartDiffRecord queued, final String userId) {
        try {
            pipelines.triggerPipeline(Map.of("WORKFLOW", "smart_diff", "MR_URL", mergeRequestUrl,
                    "PROJECT_ID", Long.toString(projectId), "MR_IID", Long.toString(iid),
                    "EXPECTED_HEAD_SHA", queued.headSha(), "EXECUTION_ID", queued.executionId(),
                    "ARNOLD_COPILOT_OWNER", userId));
            return toResponse(queued);
        } catch (RuntimeException failure) {
            return failDispatch(queued);
        }
    }

    private SmartDiffResponse failDispatch(final SmartDiffRecord queued) {
        for (var attempt = 0; attempt < MAX_SYNC_ATTEMPTS; attempt++) {
            final var current = smartDiffStore.getWithVersion(queued.id());
            if (current.isEmpty() || !queued.executionId().equals(current.get().value().executionId())
                    || !"queued".equals(current.get().value().status())) {
                return current.map(value -> toResponse(value.value())).orElseGet(() -> toResponse(queued));
            }
            final var failed = new SmartDiffRecord(queued.id(), queued.headSha(), queued.executionId(), "failed", null,
                    List.of(), System.currentTimeMillis(), DISPATCH_ERROR, "failed", 0);
            try {
                smartDiffStore.checkAndUpdate(failed, current.get().version());
                return toResponse(failed);
            } catch (ConcurrentUpdateException conflict) {
                continue;
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private ChangeRequestRecord toRecord(final MergeRequestResponse mergeRequest) {
        return new ChangeRequestRecord(mergeRequest.id(), VersionControlProviderType.GITLAB,
                mergeRequest.repositoryId(), mergeRequest.providerId(), mergeRequest.number(),
                mergeRequest.repositoryPath(), mergeRequest.title(), mergeRequest.description(), mergeRequest.webUrl(),
                mergeRequest.sourceBranch(), mergeRequest.targetBranch(), mergeRequest.headSha(), mergeRequest.state(),
                mergeRequest.mergeStatus(), mergeRequest.draft(), mergeRequest.authorId(), mergeRequest.authorName(),
                mergeRequest.assigneeIds(), mergeRequest.assigneeNames(), mergeRequest.reviewerIds(),
                mergeRequest.reviewerNames(), List.of(), mergeRequest.labels(), mergeRequest.createdAt(),
                mergeRequest.mergedAt(), mergeRequest.closedAt(), mergeRequest.hasConflicts(),
                mergeRequest.blockingDiscussionsResolved(), mergeRequest.providerUpdatedAt(),
                mergeRequest.lastSyncedAt(), record(mergeRequest.id()).map(ChangeRequestRecord::projectArchived).orElse(false));
    }

    private String reviewerState(final String state) {
        return switch (state == null ? "" : state) {
            case "review_started" -> "review_started";
            case "reviewed" -> "reviewed";
            case "requested_changes" -> "requested_changes";
            case "approved" -> "approved";
            default -> "awaiting_review";
        };
    }

    private boolean needsUserNames(final ChangeRequestRecord mergeRequest) {
        return mergeRequest.authorId() != null && mergeRequest.authorName() == null
                || !mergeRequest.assigneeIds().isEmpty() && mergeRequest.assigneeNames().isEmpty()
                || !mergeRequest.reviewerIds().isEmpty() && mergeRequest.reviewerNames().isEmpty();
    }
}
