package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.scm.Note;
import com.phonepe.devx.arnold.models.scm.Project;
import com.phonepe.devx.arnold.models.scm.User;

import java.util.Optional;

/**
 * Looks up project, user and review-comment metadata from the SCM to enrich review feedback.
 * Implementations return {@link Optional#empty()} when the entity cannot be resolved.
 */
public interface ScmMetadataService {

    Optional<Project> getProject(int projectId);

    Optional<User> getUser(int userId);

    Optional<Note> getMergeRequestNote(int projectId, int mrIid, int noteId);
}
