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

package com.phonepe.arnold.core.services;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.events.ArnoldSignalPublisher;
import com.phonepe.arnold.core.model.analysis.GroupAnalysisRun;
import com.phonepe.arnold.core.model.events.FlowJobUsageReported;
import com.phonepe.arnold.core.model.events.NotificationRequested;
import com.phonepe.arnold.core.model.mergequeue.MergeRequestGroup;
import com.phonepe.arnold.core.model.notification.NotificationType;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.impl.ProjectionQueue;
import com.phonepe.arnold.core.storage.aerospike.AerospikeGroupAnalysisRunStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeMergeRequestGroupStore;
import com.phonepe.arnold.models.analysis.AnalysisPersonaResponse;
import com.phonepe.arnold.models.analysis.GroupAnalysisResponse;
import com.phonepe.arnold.models.analysis.GroupAnalysisResponse.*;
import com.phonepe.arnold.models.analysis.TokenUsage;
import lombok.AllArgsConstructor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.function.UnaryOperator;

@Singleton
@AllArgsConstructor(onConstructor_ = @Inject)
public class GroupAnalysisService {
    private static final long EXECUTION_TIMEOUT = 3 * 60 * 60 * 1000L;
    private static final String QUEUED = "queued";
    private static final String FAILED = "failed";
    private static final String POSTED = "posted";
    private static final String REVIEWED = "reviewed";
    private static final Set<String> ACTIVE = Set.of(QUEUED, "running");
    private final AerospikeGroupAnalysisRunStore store;
    private final AerospikeMergeRequestGroupStore groups;
    private final ChangeRequestService changeRequests;
    private final PersonaService personas;
    private final RunnerDispatcher pipelines;
    private final LeaderElectionService locks;
    private final ArnoldSignalPublisher signals;
    private final ProjectionQueue projections;

    public GroupAnalysisResponse start(final String userId, final String groupId, final StartRequest request) {
        final var group = group(groupId);
        final var members = snapshot(userId, group);
        final var selected = personas.resolveForAnalysis(userId, request.reviewPersonaIds()).stream()
                .map(persona -> new Persona(persona.id(), persona.name(), persona.instructions(), persona.version())).toList();
        final var lockId = "group-analysis:" + groupId;
        final var contender = UUID.randomUUID().toString();
        require(locks.acquireOrRenew(lockId, contender, 120_000).leader(), ArnoldErrorCode.CONFLICT_ERROR);
        try {
            final var existing = activeRun(groupId);
            if (existing.isPresent()) {
                final var run = existing.get();
                require(run.context().equals(request) && run.reviewPersonas().equals(selected)
                        && sameRevisions(run.result().members(), members), ArnoldErrorCode.CONFLICT_ERROR);
                return run.result().withReviewPersonas(analysisPersonas(run.reviewPersonas()));
            }
            final var now = System.currentTimeMillis();
            final var id = UUID.randomUUID().toString();
            final var response = new GroupAnalysisResponse(id, groupId, group.name(), UUID.randomUUID().toString(),
                    QUEUED, members, Map.of(), List.of(), analysisPersonas(selected), null, null, null, false, now, now, request.execution());
            final var run = new GroupAnalysisRun(id, userId, request, selected, response, null, 0, false, now, Map.of(), false, Map.of());
            store.create(run);
            project(run);
            return dispatch(run);
        } finally {
            locks.release(lockId, contender);
        }
    }

    public List<GroupAnalysisResponse> list(final String groupId, final Long before, final int limit) {
        // ponytail: scan matches single-group history; add a group index when run volume requires it.
        return store.list().stream()
                .filter(run -> groupId == null || groupId.equals(run.result().groupId()))
                .filter(run -> before == null || run.result().createdAt() < before)
                .sorted(Comparator.comparingLong((GroupAnalysisRun run) -> run.result().createdAt()).reversed())
                .limit(Math.max(1, Math.min(limit, 100)))
                .map(this::view).toList();
    }

    public GroupAnalysisResponse get(final String runId) {
        final var run = run(runId);
        final var result = view(run);
        try {
            return result.withStale(result.stale() || isStale(run, true));
        } catch (RuntimeException unavailable) {
            return result.withStale(true).withDetail("Current group revisions could not be verified. Saved results remain available.");
        }
    }

    public Manifest manifest(final String runId, final String executionId) {
        final var run = run(runId);
        require(run.result().executionId().equals(executionId) && active(run), ArnoldErrorCode.CONFLICT_ERROR);
        return new Manifest(run.result(), run.context(), run.reviewPersonas(), run.publicationOnly(), run.memberReviews());
    }

    public GroupAnalysisResponse retry(final String userId, final String runId) {
        final var run = owned(userId, runId);
        require(Set.of("partial", FAILED).contains(view(run).runStatus()), ArnoldErrorCode.CONFLICT_ERROR);
        require(!isStale(run, true), ArnoldErrorCode.CONFLICT_ERROR);
        final var lockId = "group-analysis:" + run.result().groupId();
        final var contender = UUID.randomUUID().toString();
        require(locks.acquireOrRenew(lockId, contender, 120_000).leader(), ArnoldErrorCode.CONFLICT_ERROR);
        try {
            require(activeRun(run.result().groupId()).isEmpty(), ArnoldErrorCode.CONFLICT_ERROR);
            final var executionId = UUID.randomUUID().toString();
            final var next = mutate(runId, current -> {
                require(current.result().executionId().equals(run.result().executionId()) && !active(current),
                        ArnoldErrorCode.CONFLICT_ERROR);
                final var publicationOnly = current.analysisComplete() || current.result().findings().stream()
                        .anyMatch(finding -> Set.of(POSTED, "unknown").contains(finding.publicationStatus()));
                return current.withResult(current.result().withExecutionId(executionId).withRunStatus(QUEUED)
                                .withFindings(publicationOnly ? current.result().findings() : List.of())
                                .withDetail(null).withPipelineId(null).withUpdatedAt(System.currentTimeMillis()))
                        .withWorkerId(null).withSequence(0).withPublicationOnly(publicationOnly)
                        .withExecutionStartedAt(System.currentTimeMillis());
            });
            return dispatch(next);
        } finally {
            locks.release(lockId, contender);
        }
    }

    public boolean fresh(final String runId, final String executionId) {
        final var run = run(runId);
        require(run.result().executionId().equals(executionId) && active(run), ArnoldErrorCode.CONFLICT_ERROR);
        return !isStale(run, true);
    }

    public GroupAnalysisResponse update(final String runId, final Update update) {
        final var next = mutate(runId, current -> applyUpdate(current, update));
        project(next);
        if (update.usage() != null) {
            signals.publish(FlowJobUsageReported.of("group_review", update.executionId(), next.id(),
                    next.result().groupName(), next.invokedBy(), next.context() == null ? null : next.context().execution(),
                    update.status(), update.usage(), next.executionStartedAt()));
        }
        if (next.sequence() == update.sequence() && !update.status().equals("running")) {
            notifyFinished(next);
        }
        return next.result();
    }

    private void notifyFinished(final GroupAnalysisRun run) {
        final var result = run.result();
        final var title = switch (result.runStatus()) {
            case "completed" -> "Group review completed";
            case "partial" -> "Group review partially published";
            case "stale" -> "Group review is stale";
            default -> "Group review failed";
        };
        final var posted = result.findings().stream()
                .filter(finding -> POSTED.equals(finding.publicationStatus())).count();
        final var failed = result.findings().stream()
                .filter(finding -> FAILED.equals(finding.publicationStatus())).count();
        final var body = new StringBuilder(result.groupName());
        if (posted > 0) {
            body.append("\n").append(posted).append(" comment").append(posted == 1 ? "" : "s").append(" posted");
        }
        if (failed > 0) {
            body.append("\n").append(failed).append(" comment").append(failed == 1 ? "" : "s")
                    .append(" failed to post");
        }
        signals.publish(new NotificationRequested("analysis_finished:" + run.id() + ":" + result.runStatus(),
                NotificationType.ANALYSIS_FINISHED, "group_analysis", run.id(), Set.of(run.invokedBy()), title,
                body.toString(), "/history?runId=" + run.id(), System.currentTimeMillis()));
    }

    static GroupAnalysisRun applyUpdate(final GroupAnalysisRun current, final Update update) {
        final var result = current.result();
        require(result.executionId().equals(update.executionId()), ArnoldErrorCode.CONFLICT_ERROR);
        require(current.workerId() == null || current.workerId().equals(update.workerId()), ArnoldErrorCode.CONFLICT_ERROR);
        if (update.sequence() <= current.sequence()) return current;
        require(active(current), ArnoldErrorCode.CONFLICT_ERROR);
        final var members = result.members().stream().collect(java.util.stream.Collectors.toMap(Member::mergeRequestId, member -> member));
        require(members.keySet().containsAll(update.memberStatuses().keySet()), ArnoldErrorCode.WRONG_INPUT_ERROR);
        final var reviews = mergedReviews(current, update, members);
        require(reviews.keySet().stream().allMatch(id -> REVIEWED.equals(update.memberStatuses().get(id))),
                ArnoldErrorCode.CONFLICT_ERROR);
        if (Boolean.TRUE.equals(update.analysisComplete())) {
            require(reviews.keySet().equals(members.keySet()), ArnoldErrorCode.WRONG_INPUT_ERROR);
        }
        require(!current.analysisComplete() || !Boolean.FALSE.equals(update.analysisComplete()), ArnoldErrorCode.CONFLICT_ERROR);
        validateFindings(update, members);
        if ("completed".equals(update.status())) {
            require(update.memberStatuses().size() == members.size()
                    && update.memberStatuses().values().stream().allMatch(REVIEWED::equals)
                    && update.findings().stream().allMatch(finding -> POSTED.equals(finding.publicationStatus())),
                    ArnoldErrorCode.WRONG_INPUT_ERROR);
        }
        for (final var previous : result.findings()) {
            if (POSTED.equals(previous.publicationStatus())) {
                require(update.findings().contains(previous), ArnoldErrorCode.CONFLICT_ERROR);
            }
        }
        final var usage = new HashMap<>(current.usageByExecution());
        if (update.usage() != null) {
            // Clio reports the running total for its execution, so the latest report replaces earlier ones.
            usage.put(update.executionId(), update.usage());
        }
        return current.withMemberReviews(reviews).withUsageByExecution(usage)
                .withAnalysisComplete(current.analysisComplete() || Boolean.TRUE.equals(update.analysisComplete()))
                .withWorkerId(update.workerId()).withSequence(update.sequence()).withResult(result
                .withRunStatus(update.status()).withMemberStatuses(Map.copyOf(update.memberStatuses()))
                .withFindings(List.copyOf(update.findings())).withDetail(update.detail())
                .withStale("stale".equals(update.status())).withUpdatedAt(System.currentTimeMillis()));
    }

    private GroupAnalysisResponse dispatch(final GroupAnalysisRun run) {
        try {
            final var pipelineId = pipelines.submit(Map.of("WORKFLOW", "group_review", "ARNOLD_RUN_ID", run.id(),
                    "EXECUTION_ID", run.result().executionId()));
            final var updated = mutate(run.id(), current -> current.result().executionId().equals(run.result().executionId())
                    ? current.withResult(current.result().withPipelineId(pipelineId)) : current);
            return updated.result().withPipelineUrl(pipelines.jobUrl(pipelineId));
        } catch (RuntimeException error) {
            mutate(run.id(), current -> current.result().executionId().equals(run.result().executionId())
                    && QUEUED.equals(current.result().runStatus())
                    ? current.withResult(current.result().withRunStatus(FAILED)
                    .withDetail("Could not start the group analysis runner job. Start a new run.")) : current);
            throw error;
        }
    }

    private GroupAnalysisResponse view(final GroupAnalysisRun run) {
        var result = run.result().withExecution(run.context() == null ? null : run.context().execution());
        if (ACTIVE.contains(result.runStatus()) && !active(run)) {
            result = result.withRunStatus(FAILED).withDetail("Analysis timed out. Retry to resume with the original context.")
                    .withUpdatedAt(Math.max(result.updatedAt() + 1, run.executionStartedAt() + EXECUTION_TIMEOUT));
        }
        return result.withPipelineUrl(result.pipelineId() == null ? null : pipelines.jobUrl(result.pipelineId()))
                .withReviewPersonas(analysisPersonas(run.reviewPersonas()))
                .withStale(result.stale() || isStale(run, false))
                .withInvokedBy(run.invokedBy())
                .withUsage(TokenUsage.merge(run.usageByExecution().values()));
    }

    private static List<AnalysisPersonaResponse> analysisPersonas(final List<Persona> personas) {
        return personas.stream()
                .map(persona -> new AnalysisPersonaResponse(persona.id(), persona.name(), persona.version()))
                .toList();
    }

    private boolean isStale(final GroupAnalysisRun run, final boolean live) {
        final var group = groups.get(run.result().groupId());
        if (group.isEmpty() || !new HashSet<>(group.get().mergeRequestIds()).equals(run.result().members().stream()
                .map(Member::mergeRequestId).collect(java.util.stream.Collectors.toSet()))) return true;
        if (live) return !sameRevisions(run.result().members(), snapshot(run.invokedBy(), group.get()));
        return run.result().members().stream().anyMatch(member -> changeRequests.record(member.mergeRequestId())
                .map(current -> !member.headSha().equals(current.headSha())
                        || !member.targetBranch().equals(current.targetBranch()) || !current.isOpen()).orElse(true));
    }

    private List<Member> snapshot(final String userId, final MergeRequestGroup group) {
        require(group.mergeRequestIds().size() >= 2, ArnoldErrorCode.WRONG_INPUT_ERROR);
        return group.mergeRequestIds().stream().map(id -> {
            final var stored = changeRequests.record(id).orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
            require(stored.provider() == VersionControlProviderType.GITLAB, ArnoldErrorCode.WRONG_INPUT_ERROR);
            final var mr = changeRequests.resolve(stored.provider(), stored.webUrl(), userId);
            require(id.equals(mr.id()) && "opened".equals(mr.state()), ArnoldErrorCode.CONFLICT_ERROR);
            final var projectId = Long.parseLong(mr.repositoryId());
            final var version = changeRequests.listDiffVersions(stored.provider(), projectId, mr.number(), userId).stream()
                    .filter(item -> Objects.equals(item.headSha(), mr.headSha()))
                    .max(Comparator.comparingLong(com.phonepe.arnold.models.mergerequest.MergeRequestDiffVersionResponse::id))
                    .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build());
            require(version.baseSha() != null && version.startSha() != null && version.headSha() != null,
                    ArnoldErrorCode.CONFLICT_ERROR);
            return new Member(id, projectId, mr.number(), mr.repositoryPath(), mr.title(), mr.description(), mr.webUrl(),
                    mr.sourceBranch(), mr.targetBranch(), version.baseSha(), version.startSha(), version.headSha());
        }).toList();
    }

    private static boolean sameRevisions(final List<Member> left, final List<Member> right) {
        return left.size() == right.size() && left.stream().allMatch(a -> right.stream().anyMatch(b ->
                a.mergeRequestId().equals(b.mergeRequestId()) && a.headSha().equals(b.headSha())
                        && a.baseSha().equals(b.baseSha()) && a.startSha().equals(b.startSha())
                        && a.targetBranch().equals(b.targetBranch())));
    }

    private MergeRequestGroup group(final String id) {
        return groups.get(id)
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
    }

    private GroupAnalysisRun owned(final String userId, final String id) {
        final var run = run(id);
        require(userId.equals(run.invokedBy()), ArnoldErrorCode.NOT_FOUND_ERROR);
        return run;
    }

    private Optional<GroupAnalysisRun> activeRun(final String groupId) {
        return store.list().stream()
                .filter(run -> groupId.equals(run.result().groupId()) && active(run)).findFirst();
    }

    private static Map<String, MemberReview> mergedReviews(final GroupAnalysisRun current, final Update update,
                                                            final Map<String, Member> members) {
        final var reviews = new HashMap<>(current.memberReviews());
        if (update.memberReviews() != null) {
            update.memberReviews().forEach((id, review) -> {
                final var member = members.get(id);
                require(member != null && member.headSha().equals(review.headSha())
                        && REVIEWED.equals(update.memberStatuses().get(id)), ArnoldErrorCode.WRONG_INPUT_ERROR);
                require(!reviews.containsKey(id) || reviews.get(id).equals(review), ArnoldErrorCode.CONFLICT_ERROR);
                reviews.put(id, review);
            });
        }
        return reviews;
    }

    private static void validateFindings(final Update update, final Map<String, Member> members) {
        final var ids = new HashSet<String>();
        for (final var finding : update.findings()) {
            final var member = members.get(finding.mergeRequestId());
            require(member != null && member.headSha().equals(finding.headSha()) && ids.add(finding.id())
                    && members.keySet().containsAll(finding.relatedMergeRequestIds()), ArnoldErrorCode.WRONG_INPUT_ERROR);
            require(finding.commentUrl() == null || finding.commentUrl().startsWith(member.webUrl() + "#note_"),
                    ArnoldErrorCode.WRONG_INPUT_ERROR);
        }
    }

    private void project(final GroupAnalysisRun run) {
        projections.enqueueBestEffort(ProjectionQueue.GROUP_ANALYSIS_RUN, run.id(), run.result().updatedAt());
    }

    private GroupAnalysisRun run(final String id) {
        return store.get(id).orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
    }

    private GroupAnalysisRun mutate(final String id, final UnaryOperator<GroupAnalysisRun> change) {
        for (var attempt = 0; attempt < 8; attempt++) {
            final var current = store.getWithVersion(id)
                    .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
            final var next = change.apply(current.value());
            if (next.equals(current.value())) return next;
            try {
                store.checkAndUpdate(next, current.version());
                return next;
            } catch (ConcurrentUpdateException ignored) {
                // Retry against the latest generation.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    private static boolean active(final GroupAnalysisRun run) {
        return ACTIVE.contains(run.result().runStatus())
                && System.currentTimeMillis() - run.executionStartedAt() < EXECUTION_TIMEOUT;
    }

    private static void require(final boolean condition, final ArnoldErrorCode code) {
        if (!condition) throw ArnoldError.raise(code).build();
    }
}
