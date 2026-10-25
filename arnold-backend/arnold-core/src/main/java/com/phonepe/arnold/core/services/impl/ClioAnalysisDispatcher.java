package com.phonepe.arnold.core.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.services.AnalysisDispatcher;
import com.phonepe.arnold.core.services.PipelineTriggerClient;
import com.phonepe.arnold.core.model.persona.ReviewPersonaSnapshot;
import com.phonepe.arnold.core.storage.aerospike.AerospikeChangeRequestStore;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class ClioAnalysisDispatcher implements AnalysisDispatcher {
    private final PipelineTriggerClient gitLabClient;
    private final AerospikeChangeRequestStore changeRequestStore;
    private final ObjectMapper objectMapper;

    @Inject
    public ClioAnalysisDispatcher(final PipelineTriggerClient gitLabClient,
                                  final AerospikeChangeRequestStore changeRequestStore,
                                  final ObjectMapper objectMapper) {
        this.gitLabClient = gitLabClient;
        this.changeRequestStore = changeRequestStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public long dispatch(final String mergeRequestId, final String runId, final String personaId, final String rfcUrl,
                         final List<String> jiraTickets, final String reviewContext,
                         final List<ReviewPersonaSnapshot> reviewPersonas) {
        if (!"improve".equals(personaId)) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
        final var mergeRequest = changeRequestStore.get(mergeRequestId)
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
        final var variables = new HashMap<>(Map.of(
                "WORKFLOW", "improve",
                "MR_LINK", mergeRequest.webUrl(),
                "ARNOLD_RUN_ID", runId,
                "ARNOLD_MERGE_REQUEST_ID", mergeRequestId
        ));
        if (rfcUrl != null && !rfcUrl.isBlank()) {
            variables.put("RFC_URL", rfcUrl);
        }
        if (jiraTickets != null && !jiraTickets.isEmpty()) {
            variables.put("JIRA_TICKETS", String.join(",", jiraTickets));
        }
        if (reviewContext != null && !reviewContext.isBlank()) {
            variables.put("REVIEW_CONTEXT", reviewContext);
        }
        if (reviewPersonas != null && !reviewPersonas.isEmpty()) {
            try {
                variables.put("REVIEW_PERSONAS_JSON", objectMapper.writeValueAsString(reviewPersonas));
            } catch (JsonProcessingException error) {
                throw new IllegalStateException("Could not serialize review personas", error);
            }
        }
        return gitLabClient.triggerPipeline(variables);
    }
}
