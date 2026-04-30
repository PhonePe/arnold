package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.cibot.models.events.gitlab.GitlabNoteDetail;
import com.phonepe.devx.cibot.models.events.gitlab.Project;
import com.phonepe.devx.cibot.models.events.gitlab.User;

import java.util.List;
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
     * Calls: GET /api/housekeeping/entity/user/gitlab/{id}
     */
    Optional<User> getUserByGitlabId(int userId);

    /**
     * Fetches merge request notes from gitlab via cibot.
     * Calls: POST /api/housekeeping/gitlab/notes?projectId={projectId}&mrIid={mrIid}
     */
    Optional<List<GitlabNoteDetail>> getMergeRequestNotes(int projectId, int mrIid, List<Integer> noteIds);
}
