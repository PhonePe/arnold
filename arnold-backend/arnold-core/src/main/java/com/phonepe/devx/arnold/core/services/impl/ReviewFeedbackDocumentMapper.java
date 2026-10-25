package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.models.es.Position;
import com.phonepe.devx.arnold.models.es.ProjectInfo;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument.ReviewFeedbackDocumentBuilder;
import com.phonepe.devx.arnold.models.scm.Note;
import com.phonepe.devx.arnold.models.scm.Note.NotePosition;
import com.phonepe.devx.arnold.models.scm.Project;
import com.phonepe.devx.arnold.models.scm.User;
import lombok.experimental.UtilityClass;

import java.util.Optional;

/**
 * Shared enrichment-to-document mapping, used by live ingest and follow-up maintenance flows so
 * the mapping stays identical.
 */
@UtilityClass
public class ReviewFeedbackDocumentMapper {

    public void applyProjectAndAuthor(final ReviewFeedbackDocumentBuilder b,
                                      final Long projectId,
                                      final Optional<Project> project,
                                      final Optional<User> mrAuthor) {
        b.mrAuthor(mrAuthor.orElse(null))
                .project(buildProjectInfo(projectId, project));
    }

    public ProjectInfo buildProjectInfo(final Long projectId, final Optional<Project> project) {
        final ProjectInfo.ProjectInfoBuilder builder = ProjectInfo.builder().projectId(projectId);
        project.ifPresent(p -> builder
                .projectName(p.getName())
                .projectNamespace(p.getNamespace())
                .teamId(p.getTeamId())
                .podName(p.getPod()));
        return builder.build();
    }

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
