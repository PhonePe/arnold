package com.phonepe.devx.arnold.server.services.impl;

import com.phonepe.devx.arnold.models.es.Position;
import com.phonepe.devx.arnold.models.es.ProjectInfo;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument.ReviewFeedbackDocumentBuilder;
import com.phonepe.devx.cibot.models.events.gitlab.Note;
import com.phonepe.devx.cibot.models.events.gitlab.Note.NotePosition;
import com.phonepe.devx.cibot.models.events.gitlab.Project;
import com.phonepe.devx.cibot.models.events.gitlab.User;
import lombok.experimental.UtilityClass;

import java.util.Optional;

/**
 * Shared enrichment-to-document mapping, used by both live ingest and the backfill job so the
 * mapping stays identical.
 */
@UtilityClass
public class ReviewFeedbackDocumentMapper {

    /**
     * Applies project (nested) and MR-author enrichment onto the builder. projectId always comes
     * from persisted metadata; the remaining fields are null-safe from the CIBOT project.
     */
    public void applyProjectAndAuthor(final ReviewFeedbackDocumentBuilder b,
                                       final Long projectId,
                                       final Optional<Project> project,
                                       final Optional<User> mrAuthor) {
        b.mrAuthor(mrAuthor.orElse(null))
                .project(buildProjectInfo(projectId, project));
    }

    /**
     * Builds the nested {@link ProjectInfo} from a persisted projectId and a null-safe CIBOT
     * project lookup. Shared by live ingest and backfill re-enrichment so the field mapping
     * cannot drift between the two call sites.
     */
    public ProjectInfo buildProjectInfo(final Long projectId, final Optional<Project> project) {
        final ProjectInfo.ProjectInfoBuilder builder = ProjectInfo.builder().projectId(projectId);
        project.ifPresent(p -> builder
                .projectName(p.getName())
                .projectNamespace(p.getNamespace())
                .teamId(p.getOlympusTeamId())
                .podName(p.getPod()));
        return builder.build();
    }

    /**
     * Applies note details onto the builder. noteId/discussionId come from the comment context;
     * the remaining fields are null-safe from the CIBOT note.
     */
    public void applyNoteDetails(final ReviewFeedbackDocumentBuilder documentBuilder,
                                 final Long noteId,
                                 final String discussionId,
                                 final Optional<Note> note) {
        documentBuilder.noteId(noteId)
                .discussionId(discussionId);
        note.ifPresent(n -> documentBuilder
                .noteCreatedAt(n.getCreatedAt())
                .comment(n.getBody())
                .position(n.getPosition() != null ? toPosition(n.getPosition()) : null)
                .reviewer(n.getAuthor()));
    }

    public Position toPosition(final NotePosition notePosition) {
        return Position.builder()
                .newPath(notePosition.getNewPath())
                .oldPath(notePosition.getOldPath())
                .newLine(notePosition.getNewLine())
                .oldLine(notePosition.getOldLine())
                .build();
    }
}
