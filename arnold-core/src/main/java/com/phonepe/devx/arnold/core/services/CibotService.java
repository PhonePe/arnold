package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.cibot.models.events.gitlab.Note;
import com.phonepe.devx.cibot.models.events.gitlab.Project;
import com.phonepe.devx.cibot.models.events.gitlab.User;

import java.util.Optional;

/**
 * Client interface for communicating with the CIBot service to fetch
 * project metadata and note information for review feedback enrichment.
 */
public interface CibotService {

    /**
     * Fetches project details by gitlab project id.
     * Calls: GET /api/housekeeping/entity/project/gitlab/{id}
     */
    Optional<Project> getProjectByGitlabId(int projectId);

    /**
     * Fetches user details by gitlab user id.
     * Calls: GET /users/{id}
     */
    Optional<User> getUserByGitlabId(int userId);

    /**
     * Fetches a single merge request note from gitlab via cibot.
     * Calls: GET /notes/projectId/{projectId}/mrIid/{mrIid}/noteId/{noteId}
     */
    Optional<Note> getMergeRequestNote(int projectId, int mrIid, int noteId);
}
