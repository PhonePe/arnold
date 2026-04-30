package com.phonepe.devx.arnold.core.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.devx.arnold.core.clients.BaseServiceClient;
import com.phonepe.devx.arnold.core.clients.ClientDeclaration;
import com.phonepe.devx.arnold.core.events.EventIngestor;
import com.phonepe.devx.arnold.core.services.CibotService;
import com.phonepe.devx.cibot.models.CIBotResponse;
import com.phonepe.devx.cibot.models.events.gitlab.GitlabNoteDetail;
import com.phonepe.devx.cibot.models.events.gitlab.Project;
import com.phonepe.devx.cibot.models.events.gitlab.User;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

@Slf4j
@Singleton
@ClientDeclaration("CIBOT")
public class CibotServiceHttpImpl extends BaseServiceClient implements CibotService {

    private static final String SERVICE_NAME = "CIBOT";

    @Inject
    public CibotServiceHttpImpl(EventIngestor eventIngestor) {
        super(SERVICE_NAME, eventIngestor);
    }

    @Override
    public Optional<Project> getProjectByGitlabId(int projectId) {
        try {
            final var response = executorFactory
                    .<CIBotResponse<Object>>httpGetExecutorBuilder()
                    .responseTypeReference(new TypeReference<>() {})
                    .url("/api/housekeeping/entity/project/gitlab/" + projectId)
                    .command("getProjectByGitlabId")
                    .nonSuccessResponseConsumer(extractedResponse ->
                            handleNonSuccessResponse(extractedResponse, "projectId=" + projectId, null))
                    .build()
                    .executeTracked();
            return Optional.ofNullable(response)
                    .filter(CIBotResponse::isSuccess)
                    .map(r -> (Project) r.getResponse());
        } catch (Exception e) {
            log.error("Failed to fetch project from cibot for projectId={}", projectId, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> getUserByGitlabId(int userId) {
        try {
            final var response = executorFactory
                    .<CIBotResponse<Object>>httpGetExecutorBuilder()
                    .responseTypeReference(new TypeReference<>() {})
                    .url("/api/housekeeping/entity/user/gitlab/" + userId)
                    .command("getUserByGitlabId")
                    .nonSuccessResponseConsumer(extractedResponse ->
                            handleNonSuccessResponse(extractedResponse, "userId=" + userId, null))
                    .build()
                    .executeTracked();
            return Optional.ofNullable(response)
                    .filter(CIBotResponse::isSuccess)
                    .map(r -> (User) r.getResponse());
        } catch (Exception e) {
            log.error("Failed to fetch user from cibot for userId={}", userId, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<List<GitlabNoteDetail>> getMergeRequestNotes(int projectId, int mrIid, List<Integer> noteIds) {
        try {
            final var response = executorFactory
                    .<CIBotResponse<List<GitlabNoteDetail>>>httpPostExecutorBuilder()
                    .responseTypeReference(new TypeReference<>() {})
                    .url("/api/housekeeping/gitlab/notes?projectId=" + projectId + "&mrIid=" + mrIid)
                    .httpData(serializableHttpData(noteIds))
                    .command("getMergeRequestNotes")
                    .nonSuccessResponseConsumer(extractedResponse ->
                            handleNonSuccessResponse(extractedResponse,
                                    "projectId=" + projectId + ", mrIid=" + mrIid, null))
                    .build()
                    .executeTracked();
            return Optional.ofNullable(response)
                    .filter(CIBotResponse::isSuccess)
                    .map(CIBotResponse::getResponse);
        } catch (Exception e) {
            log.error("Failed to fetch MR notes from cibot for projectId={}, mrIid={}", projectId, mrIid, e);
            return Optional.empty();
        }
    }
}
