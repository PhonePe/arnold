package com.phonepe.devx.arnold.server.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.devx.arnold.core.services.CibotService;
import com.phonepe.devx.arnold.core.services.ReviewFeedbackService;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.devx.arnold.models.request.ReviewComment;
import com.phonepe.devx.arnold.models.request.ReviewFeedbackRequest;
import com.phonepe.devx.arnold.models.request.ReviewMetadata;
import com.phonepe.devx.cibot.models.events.gitlab.GitlabNoteDetail;
import com.phonepe.devx.cibot.models.events.gitlab.Project;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Singleton
@AllArgsConstructor(onConstructor_ = @Inject)
public class ReviewFeedbackServiceImpl implements ReviewFeedbackService {

    private final CibotService cibotService;

    @Override
    public int ingestFeedback(ReviewFeedbackRequest request) {
        final var metadata = request.getMetadata();
        log.info("Received {} review feedback comments for MR {} in project {}",
                request.getComments().size(), metadata.getMergeRequestId(), metadata.getProjectId());

        // Fetch project info via GET /api/housekeeping/entity/project/gitlab/{id}
        final var project = cibotService.getProjectByGitlabId(metadata.getProjectId().intValue()).orElse(null);
        if (project == null) {
            log.warn("Could not fetch project info from cibot for projectId={}", metadata.getProjectId());
        }

        // Fetch notes via POST /api/housekeeping/gitlab/notes
        final var noteIds = request.getComments().stream()
                .map(c -> c.getNoteId().intValue())
                .toList();

        final Map<Integer, GitlabNoteDetail> notesMap;
        if (!noteIds.isEmpty()) {
            final var notesList = cibotService.getMergeRequestNotes(
                    metadata.getProjectId().intValue(),
                    metadata.getMergeRequestId().intValue(),
                    noteIds
            ).orElse(Collections.emptyList());

            notesMap = notesList.stream()
                    .collect(Collectors.toMap(GitlabNoteDetail::getId, Function.identity(), (a, b) -> a));
        } else {
            notesMap = Collections.emptyMap();
        }

        final var documents = buildDocuments(request.getComments(), metadata, project, notesMap);

        // TODO: persist documents to Elasticsearch via Columbus
        log.info("Built {} review feedback documents for persistence", documents.size());
        return documents.size();
    }

    private List<ReviewFeedbackDocument> buildDocuments(
            List<ReviewComment> comments,
            ReviewMetadata metadata,
            Project project,
            Map<Integer, GitlabNoteDetail> notesMap) {

        final var now = System.currentTimeMillis();

        return comments.stream()
                .map((ReviewComment comment) -> {
                    final var noteDetail = notesMap.get(comment.getNoteId().intValue());
                    final var position = noteDetail != null ? noteDetail.getPosition() : null;
                    final var author = noteDetail != null ? noteDetail.getAuthor() : null;

                    return ReviewFeedbackDocument.builder()
                            .projectId(metadata.getProjectId())
                            .mergeRequestId(metadata.getMergeRequestId())
                            .mrAuthorId(metadata.getMrAuthorId())
                            .mrAuthor(author)
                            .projectName(project != null ? project.getName() : null)
                            .projectNamespace(project != null ? project.getNamespace() : null)
                            .teamId(project != null ? project.getOlympusTeamId() : null)
                            .podName(project != null ? project.getPod() : null)
                            .noteId(comment.getNoteId())
                            .discussionId(comment.getDiscussionId())
                            .noteCreatedAt(noteDetail != null ? noteDetail.getCreatedAt() : null)
                            .comment(noteDetail != null ? noteDetail.getBody() : null)
                            .file(position != null ? position.getNewPath() : null)
                            .line(position != null ? position.getNewLine() : null)
                            .reviewer(author)
                            .commentType(comment.getCommentType())
                            .hasSuggestion(comment.getHasSuggestion())
                            .acceptanceStatus(comment.getStatus())
                            .score(comment.getScore() != null ? comment.getScore().doubleValue() : null)
                            .reasoning(comment.getReasoning())
                            .priority(comment.getPriority())
                            .commentCategory(comment.getCommentCategory())
                            .ingestedAt(now)
                            .build();
                })
                .toList();
    }
}
