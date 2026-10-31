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

package com.phonepe.arnold.core.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.config.AnalysisConfiguration.PersonaDelivery;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.services.AnalysisDispatcher;
import com.phonepe.arnold.core.services.RunnerDispatcher;
import com.phonepe.arnold.core.model.persona.ReviewPersonaSnapshot;
import com.phonepe.arnold.core.storage.aerospike.AerospikeChangeRequestStore;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class ClioAnalysisDispatcher implements AnalysisDispatcher {
    private final RunnerDispatcher gitLabClient;
    private final AerospikeChangeRequestStore changeRequestStore;
    private final ObjectMapper objectMapper;
    private final ArnoldCoreConfiguration config;

    @Inject
    public ClioAnalysisDispatcher(final RunnerDispatcher gitLabClient,
                                  final AerospikeChangeRequestStore changeRequestStore,
                                  final ObjectMapper objectMapper,
                                  final ArnoldCoreConfiguration config) {
        this.gitLabClient = gitLabClient;
        this.changeRequestStore = changeRequestStore;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    public static String personaRefs(final List<ReviewPersonaSnapshot> personas) {
        return String.join(",", personas.stream().map(persona -> persona.id() + "@" + persona.version()).toList());
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
        if (reviewPersonas != null && !reviewPersonas.isEmpty()
                && config.getAnalysisConfiguration().getPersonaDelivery() == PersonaDelivery.FETCH) {
            variables.put("REVIEW_PERSONA_REFS", personaRefs(reviewPersonas));
        } else if (reviewPersonas != null && !reviewPersonas.isEmpty()) {
            try {
                variables.put("REVIEW_PERSONAS_JSON", objectMapper.writeValueAsString(reviewPersonas));
            } catch (JsonProcessingException error) {
                throw new IllegalStateException("Could not serialize review personas", error);
            }
        }
        return gitLabClient.submit(variables);
    }
}
