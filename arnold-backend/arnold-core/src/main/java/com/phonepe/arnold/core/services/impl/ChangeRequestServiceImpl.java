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

package com.phonepe.arnold.core.services.impl;
import com.phonepe.arnold.core.services.RunnerDispatcher;
import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.review.ReviewProgress;
import com.phonepe.arnold.core.model.review.SmartDiffRecord;
import com.phonepe.arnold.core.events.ArnoldSignalPublisher;
import com.phonepe.arnold.core.model.events.FlowJobUsageReported;
import com.phonepe.arnold.core.model.events.NotificationRequested;
import com.phonepe.arnold.core.model.notification.NotificationType;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.provider.VersionControlProviders;
import com.phonepe.arnold.core.services.AnalysisExecutionService;
import com.phonepe.arnold.core.services.ChangeRequestService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeChangeRequestStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeReviewProgressStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeSmartDiffStore;
import com.phonepe.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestFilesResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestDiffVersionResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestReviewResponse;
import com.phonepe.arnold.models.mergerequest.ReviewerStatusResponse;
import com.phonepe.arnold.models.mergerequest.ReviewerCandidateResponse;
import com.phonepe.arnold.models.mergerequest.ViewedFilesRequest;
import com.phonepe.arnold.models.mergerequest.ViewedFilesResponse;
import com.phonepe.arnold.models.mergerequest.SmartDiffRequest;
import com.phonepe.arnold.models.mergerequest.SmartDiffResponse;
import com.phonepe.arnold.models.mergerequest.SmartDiffResultRequest;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Singleton
public class ChangeRequestServiceImpl implements ChangeRequestService {
    private static final int MAX_SYNC_ATTEMPTS = 8;
    private static final long SMART_DIFF_TIMEOUT_MS = 135 * 60 * 1000L;
    private static final String APPROVED = "approved";
    private static final String COMPLETED = "completed";
    private static final String FAILED = "failed";
    private static final String QUEUED = "queued";
    private static final String RUNNING = "running";
    private static final String SMART_DIFF = "smart_diff";
    private static final String DISPATCH_ERROR = "Could not start Smart Diff generation. Please retry.";

    private final AerospikeChangeRequestStore mergeRequestStore;
    private final VersionControlProviders providers;
    private final AerospikeReviewProgressStore reviewProgressStore;
    private final AerospikeSmartDiffStore smartDiffStore;
    private final RunnerDispatcher pipelines;
    private final ArnoldSignalPublisher signals;
    private final AnalysisExecutionService executions;
    private final ProjectionQueue projections;

    @Inject
    public ChangeRequestServiceImpl(final AerospikeChangeRequestStore mergeRequestStore,
                                    final VersionControlProviders providers,
                                    final AerospikeReviewProgressStore reviewProgressStore,
                                    final AerospikeSmartDiffStore smartDiffStore,
                                    final RunnerDispatcher pipelines,
                                    final ArnoldSignalPublisher signals,
                                    final AnalysisExecutionService executions,
                                    final ProjectionQueue projections) {
        this.mergeRequestStore = mergeRequestStore;
        this.providers = providers;
        this.reviewProgressStore = reviewProgressStore;
        this.smartDiffStore = smartDiffStore;
        this.pipelines = pipelines;
        this.signals = signals;
        this.executions = executions;
        this.projections = projections;
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
    public MergeRequestResponse addReviewer(final VersionControlProviderType provider, final long projectId, final long iid, final String username,
                                            final String userId) {
        return providers.get(provider)
                .addChangeRequestReviewer(projectId, iid, username, userId);
    }

    @Override
    public MergeRequestResponse removeReviewer(final VersionControlProviderType provider, final long projectId, final long iid, final long reviewerId,
                                               final String userId) {
        return providers.get(provider)
                .removeChangeRequestReviewer(projectId, iid, reviewerId, userId);
    }

    @Override
    public List<ReviewerCandidateResponse> searchReviewers(final VersionControlProviderType provider, final long projectId, final String query,
                                                           final String userId) {
        return providers.get(provider)
                .searchChangeRequestReviewers(projectId, query, userId);
    }

    @Override
    public MergeRequestReviewResponse getReview(final VersionControlProviderType provider, final long projectId, final long iid, final String userId) {
        final var review = providers.get(provider)
                .getChangeRequestReview(projectId, iid, userId);
        final var stored = mergeRequestStore.get(ChangeRequestRecord.key(provider,
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
                        APPROVED.equals(reviewer.status()) ? APPROVED
                                : states.getOrDefault(reviewer.id(), reviewer.status()))).toList());
    }

    @Override
    public String getDiff(final VersionControlProviderType provider, final long projectId, final long iid, final String userId) {
        return providers.get(provider).getChangeRequestDiff(projectId, iid, userId);
    }

    @Override
    public String getDiffVersion(final VersionControlProviderType provider, final long projectId, final long iid, final long versionId, final String userId) {
        return providers.get(provider)
                .getChangeRequestDiffVersion(projectId, iid, versionId, userId);
    }

    @Override
    public List<MergeRequestDiffVersionResponse> listDiffVersions(final VersionControlProviderType provider, final long projectId, final long iid,
                                                                  final String userId) {
        return providers.get(provider).listChangeRequestDiffVersions(projectId, iid, userId);
    }

    @Override
    public MergeRequestFilesResponse getFiles(final VersionControlProviderType provider, final long projectId, final long iid, final String oldPath,
                                              final String newPath, final String baseSha, final String headSha,
                                              final String userId) {
        return providers.get(provider)
                .getChangeRequestFiles(projectId, iid, oldPath, newPath, baseSha, headSha, userId);
    }

    @Override
    public ViewedFilesResponse getViewedFiles(final VersionControlProviderType provider, final long projectId, final long iid, final String headSha,
                                              final String userId) {
        return reviewProgressStore.get(ReviewProgress.key(provider, userId, projectId, iid, headSha))
                .map(this::toResponse)
                .orElseGet(() -> new ViewedFilesResponse(headSha, List.of(), List.of(), Map.of(), 0));
    }

    @Override
    public ViewedFilesResponse updateViewedFiles(final VersionControlProviderType provider, final long projectId, final long iid,
                                                 final ViewedFilesRequest request, final String userId) {
        final var id = ReviewProgress.key(provider, userId, projectId, iid, request.getHeadSha());
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
        projections.enqueueBestEffort(ProjectionQueue.REVIEW_PROGRESS, id, progress.updatedAt());
        return toResponse(progress);
    }

    @Override
    public SmartDiffResponse getSmartDiff(final VersionControlProviderType provider, final long projectId, final long iid, final String headSha) {
        return smartDiffStore.get(SmartDiffRecord.key(provider, projectId, iid, headSha))
                .map(this::toResponse)
                .orElseGet(() -> new SmartDiffResponse(headSha, "not_requested", null, List.of(), 0, null,
                        "not_requested", 0));
    }

    @Override
    public SmartDiffResponse requestSmartDiff(final VersionControlProviderType provider, final long projectId, final long iid, final SmartDiffRequest request,
                                              final String userId) {
        final var id = SmartDiffRecord.key(provider, projectId, iid, request.getHeadSha());
        executions.validate(userId, request.getExecution());
        final var vcs = providers.get(provider);
        final var stored = mergeRequestStore.get(ChangeRequestRecord.key(provider,
                        Long.toString(projectId), iid))
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
        final var live = vcs.resolveChangeRequest(stored.webUrl(), userId);
        if (!Long.toString(projectId).equals(live.repositoryId()) || iid != live.number()
                || !request.getHeadSha().equalsIgnoreCase(live.headSha())) {
            throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        }
        synchronize(toRecord(live));
        for (var attempt = 0; attempt < MAX_SYNC_ATTEMPTS; attempt++) {
            final var response = attemptSmartDiff(provider, live, projectId, iid, request, userId, id);
            if (response.isPresent()) {
                return response.get();
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private Optional<SmartDiffResponse> attemptSmartDiff(final VersionControlProviderType provider,
                                                         final MergeRequestResponse live, final long projectId,
                                                         final long iid, final SmartDiffRequest request,
                                                         final String userId, final String id) {
        final var current = smartDiffStore.getWithVersion(id);
        final var queued = queued(id, request.getHeadSha(), userId, request.getExecution());
        if (current.isEmpty()) {
            try {
                smartDiffStore.create(queued);
            } catch (RuntimeException createRace) {
                return Optional.empty();
            }
            projectSmartDiff(queued);
            return Optional.of(dispatch(provider, live.webUrl(), projectId, iid, queued, userId));
        }
        final var value = current.get().value();
        final var regenerate = request.isRegenerate() && COMPLETED.equals(value.status());
        if (!regenerate && !FAILED.equals(value.status()) && !isStale(value)) {
            return Optional.of(toResponse(value));
        }
        try {
            smartDiffStore.checkAndUpdate(queued, current.get().version());
            projectSmartDiff(queued);
            return Optional.of(dispatch(provider, live.webUrl(), projectId, iid, queued, userId));
        } catch (ConcurrentUpdateException conflict) {
            return Optional.empty();
        }
    }

    @Override
    public SmartDiffResponse updateSmartDiff(final VersionControlProviderType provider, final long projectId, final long iid,
                                             final SmartDiffResultRequest request) {
        final var id = SmartDiffRecord.key(provider, projectId, iid, request.getHeadSha());
        for (var attempt = 0; attempt < MAX_SYNC_ATTEMPTS; attempt++) {
            final var current = smartDiffStore.getWithVersion(id)
                    .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
            if (!request.getExecutionId().equals(current.value().executionId())) {
                throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
            }
            final var settled = settledResponse(current.value(), request);
            if (settled.isPresent()) {
                return settled.get();
            }
            if (COMPLETED.equals(request.getStatus())) {
                final var mergeRequest = mergeRequestStore.get(ChangeRequestRecord.key(provider,
                                Long.toString(projectId), iid))
                        .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
                if (!request.getHeadSha().equalsIgnoreCase(mergeRequest.headSha())) {
                    throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
                }
            }
            final var updated = new SmartDiffRecord(id, request.getHeadSha(), request.getExecutionId(),
                    request.getStatus(), request.getSummary(), request.getGroups(), System.currentTimeMillis(),
                    request.getErrorMessage(),
                    orElse(request.getCommentAssessmentStatus(),
                            current.value().commentAssessmentStatus()),
                    orElse(request.getAssessedCommentCount(),
                            current.value().assessedCommentCount()),
                    current.value().requestedBy(), current.value().execution(), current.value().requestedAt(),
                    orElse(request.getUsage(), current.value().usage()),
                    current.value().pipelineId());
            try {
                smartDiffStore.checkAndUpdate(updated, current.version());
                projectSmartDiff(updated);
                if (request.getUsage() != null) {
                    signals.publish(FlowJobUsageReported.of(SMART_DIFF, updated.executionId(), id,
                            ChangeRequestRecord.key(provider, Long.toString(projectId), iid),
                            updated.requestedBy(), updated.execution(), updated.status(), updated.usage(),
                            updated.requestedAt()));
                }
                notifySmartDiffFinished(updated);
                return toResponse(updated);
            } catch (ConcurrentUpdateException conflict) {
                // Retry against the latest Aerospike generation.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private static <T> T orElse(final T value, final T fallback) {
        return value == null ? fallback : value;
    }

    private Optional<SmartDiffResponse> settledResponse(final SmartDiffRecord value,
                                                        final SmartDiffResultRequest request) {
        final var status = value.status();
        if (status.equals(request.getStatus())) {
            return Optional.of(toResponse(value));
        }
        final var terminal = COMPLETED.equals(status) || FAILED.equals(status);
        if (terminal || !(QUEUED.equals(status) || RUNNING.equals(status) && !RUNNING.equals(request.getStatus()))) {
            throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        }
        return Optional.empty();
    }

    @Override
    public boolean synchronize(final ChangeRequestRecord mergeRequest) {
        for (var attempt = 0; attempt < MAX_SYNC_ATTEMPTS; attempt++) {
            final var current = mergeRequestStore.getWithVersion(mergeRequest.id());
            if (current.isEmpty()) {
                if (tryCreate(mergeRequest)) {
                    return true;
                }
                continue;
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
                mergeRequestStore.checkAndUpdate(carryReviewerStates(mergeRequest, previous), current.get().version());
                return true;
            } catch (ConcurrentUpdateException conflict) {
                // Retry against the latest Aerospike generation.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.DB_ERROR).build();
    }

    private boolean tryCreate(final ChangeRequestRecord mergeRequest) {
        try {
            mergeRequestStore.create(mergeRequest);
            return true;
        } catch (RuntimeException createRace) {
            return false;
        }
    }

    private ChangeRequestRecord carryReviewerStates(final ChangeRequestRecord mergeRequest,
                                                    final ChangeRequestRecord previous) {
        if (!mergeRequest.reviewerStates().isEmpty() || previous.reviewerStates().isEmpty()) {
            return mergeRequest;
        }
        return mergeRequest.withReviewerStates(mergeRequest.reviewerIds().stream().map(id -> {
            final var index = previous.reviewerIds().indexOf(id);
            if (index >= 0 && index < previous.reviewerStates().size()) {
                return previous.reviewerStates().get(index);
            }
            return "unreviewed";
        }).toList());
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
                smartDiff.assessedCommentCount(), smartDiff.usage(), smartDiff.pipelineId(),
                smartDiff.pipelineId() == null ? null : pipelines.jobUrl(smartDiff.pipelineId()));
    }

    private SmartDiffRecord queued(final String id, final String headSha, final String userId,
                                   final com.phonepe.arnold.models.analysis.ExecutionOptions execution) {
        final var now = System.currentTimeMillis();
        return new SmartDiffRecord(id, headSha, UUID.randomUUID().toString(), QUEUED, null, List.of(),
                now, null, QUEUED, 0, userId, execution, now);
    }

    private void projectSmartDiff(final SmartDiffRecord smartDiff) {
        projections.enqueueBestEffort(ProjectionQueue.SMART_DIFF, smartDiff.id(), smartDiff.updatedAt());
    }

    private void notifySmartDiffFinished(final SmartDiffRecord smartDiff) {
        if (!COMPLETED.equals(smartDiff.status()) && !FAILED.equals(smartDiff.status())) {
            return;
        }
        if (smartDiff.requestedBy() == null) {
            return;
        }
        final var parts = smartDiff.id().split(":");
        final var provider = parts[1].toUpperCase(java.util.Locale.ROOT);
        final var projectId = parts[2];
        final var iid = parts[3];
        final var completed = COMPLETED.equals(smartDiff.status());
        final var title = completed ? "Story mode generation completed" : "Story mode generation failed";
        final var body = completed ? null : smartDiff.errorMessage();
        signals.publish(new NotificationRequested("smart_diff:" + smartDiff.executionId(),
                NotificationType.ANALYSIS_FINISHED, SMART_DIFF, smartDiff.id(), Set.of(smartDiff.requestedBy()),
                title, body, "/review/" + provider + "/" + projectId + "/" + iid,
                System.currentTimeMillis()));
    }

    private boolean isStale(final SmartDiffRecord record) {
        return (QUEUED.equals(record.status()) || RUNNING.equals(record.status()))
                && System.currentTimeMillis() - record.updatedAt() > SMART_DIFF_TIMEOUT_MS;
    }

    private SmartDiffResponse dispatch(final VersionControlProviderType provider, final String mergeRequestUrl,
                                       final long projectId, final long iid,
                                       final SmartDiffRecord queued, final String userId) {
        final long pipelineId;
        try {
            pipelineId = pipelines.submit(Map.of("WORKFLOW", SMART_DIFF, "PROVIDER", provider.name(),
                    "MR_URL", mergeRequestUrl,
                    "PROJECT_ID", Long.toString(projectId), "MR_IID", Long.toString(iid),
                    "EXPECTED_HEAD_SHA", queued.headSha(), "EXECUTION_ID", queued.executionId(),
                    "ARNOLD_COPILOT_OWNER", userId));
        } catch (RuntimeException failure) {
            return failDispatch(queued);
        }
        return toResponse(attachPipeline(queued, pipelineId));
    }

    private SmartDiffRecord attachPipeline(final SmartDiffRecord queued, final long pipelineId) {
        final var dispatched = queued.withPipelineId(pipelineId);
        for (var attempt = 0; attempt < MAX_SYNC_ATTEMPTS; attempt++) {
            final var current = smartDiffStore.getWithVersion(queued.id());
            if (current.isEmpty() || !queued.executionId().equals(current.get().value().executionId())) {
                return dispatched;
            }
            final var updated = current.get().value().withPipelineId(pipelineId);
            try {
                smartDiffStore.checkAndUpdate(updated, current.get().version());
                return updated;
            } catch (ConcurrentUpdateException conflict) {
                // Retry against the latest Aerospike generation.
            } catch (RuntimeException failure) {
                log.warn("Could not record smart diff pipeline {} for {}", pipelineId, queued.id(), failure);
                return dispatched;
            }
        }
        return dispatched;
    }

    private SmartDiffResponse failDispatch(final SmartDiffRecord queued) {
        for (var attempt = 0; attempt < MAX_SYNC_ATTEMPTS; attempt++) {
            final var current = smartDiffStore.getWithVersion(queued.id());
            if (current.isEmpty() || !queued.executionId().equals(current.get().value().executionId())
                    || !QUEUED.equals(current.get().value().status())) {
                return current.map(value -> toResponse(value.value())).orElseGet(() -> toResponse(queued));
            }
            final var failed = new SmartDiffRecord(queued.id(), queued.headSha(), queued.executionId(), FAILED, null,
                    List.of(), System.currentTimeMillis(), DISPATCH_ERROR, FAILED, 0, queued.requestedBy(),
                    queued.execution(), queued.requestedAt());
            try {
                smartDiffStore.checkAndUpdate(failed, current.get().version());
                projectSmartDiff(failed);
                return toResponse(failed);
            } catch (ConcurrentUpdateException conflict) {
                // Retry against the latest Aerospike generation.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private ChangeRequestRecord toRecord(final MergeRequestResponse mergeRequest) {
        return new ChangeRequestRecord(mergeRequest.id(), providerOf(mergeRequest),
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

    private static VersionControlProviderType providerOf(final MergeRequestResponse mergeRequest) {
        return mergeRequest.provider() == null ? VersionControlProviderType.GITLAB
                : VersionControlProviderType.valueOf(mergeRequest.provider().toUpperCase(java.util.Locale.ROOT));
    }

    private String reviewerState(final String state) {
        return switch (state == null ? "" : state) {
            case "review_started" -> "review_started";
            case "reviewed" -> "reviewed";
            case "requested_changes" -> "requested_changes";
            case APPROVED -> APPROVED;
            default -> "awaiting_review";
        };
    }

    private boolean needsUserNames(final ChangeRequestRecord mergeRequest) {
        return mergeRequest.authorId() != null && mergeRequest.authorName() == null
                || !mergeRequest.assigneeIds().isEmpty() && mergeRequest.assigneeNames().isEmpty()
                || !mergeRequest.reviewerIds().isEmpty() && mergeRequest.reviewerNames().isEmpty();
    }
}
