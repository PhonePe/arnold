package com.phonepe.devx.arnold.core.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.phonepe.devx.arnold.core.clients.BaseServiceClient;
import com.phonepe.devx.arnold.core.clients.ClientDeclaration;
import com.phonepe.devx.arnold.core.events.EventIngestor;
import com.phonepe.devx.arnold.core.services.CibotService;
import com.phonepe.devx.cibot.models.CIBotResponse;
import com.phonepe.devx.cibot.models.events.gitlab.Note;
import com.phonepe.devx.cibot.models.events.gitlab.Project;
import com.phonepe.devx.cibot.models.events.gitlab.User;
import lombok.extern.slf4j.Slf4j;

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
                    .<CIBotResponse<Project>>httpGetExecutorBuilder()
                    .responseTypeReference(new TypeReference<CIBotResponse<Project>>() {})
                    .url("/projects/" + projectId)
                    .command("getProjectByGitlabId")
                    .nonSuccessResponseConsumer(extractedResponse ->
                            handleNonSuccessResponse(extractedResponse, "projectId=" + projectId, null))
                    .build()
                    .executeTracked();
            return Optional.ofNullable(response)
                    .filter(CIBotResponse::isSuccess)
                    .map(CIBotResponse::getResponse);
        } catch (Exception e) {
            log.error("Failed to fetch project from cibot for projectId={}", projectId, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> getUserByGitlabId(int userId) {
        try {
            final var response = executorFactory
                    .<CIBotResponse<User>>httpGetExecutorBuilder()
                    .responseTypeReference(new TypeReference<CIBotResponse<User>>() {})
                    .url("/users/" + userId)
                    .command("getUserByGitlabId")
                    .nonSuccessResponseConsumer(extractedResponse ->
                            handleNonSuccessResponse(extractedResponse, "userId=" + userId, null))
                    .build()
                    .executeTracked();
            return Optional.ofNullable(response)
                    .filter(CIBotResponse::isSuccess)
                    .map(CIBotResponse::getResponse);
        } catch (Exception e) {
            log.error("Failed to fetch user from cibot for userId={}", userId, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Note> getMergeRequestNote(int projectId, int mrIid, int noteId) {
        try {
            final var response = executorFactory
                    .<CIBotResponse<Note>>httpGetExecutorBuilder()
                    .responseTypeReference(new TypeReference<>() {})
                    .url("/notes/projectId/" + projectId + "/mrIid/" + mrIid + "/noteId/" + noteId)
                    .command("getMergeRequestNote")
                    .nonSuccessResponseConsumer(extractedResponse ->
                            handleNonSuccessResponse(extractedResponse,
                                    "projectId=" + projectId + ", mrIid=" + mrIid + ", noteId=" + noteId, null))
                    .build()
                    .executeTracked();
            return Optional.ofNullable(response)
                    .filter(CIBotResponse::isSuccess)
                    .map(CIBotResponse::getResponse);
        } catch (Exception e) {
            log.error("Failed to fetch MR note from cibot for projectId={}, mrIid={}, noteId={}", projectId, mrIid, noteId, e);
            return Optional.empty();
        }
    }
}
