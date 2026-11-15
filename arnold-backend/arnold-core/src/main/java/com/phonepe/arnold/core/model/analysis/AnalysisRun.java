/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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

package com.phonepe.arnold.core.model.analysis;

import com.phonepe.arnold.models.analysis.ExecutionOptions;

import com.livetheoogway.crudstore.core.Id;
import com.phonepe.arnold.core.model.persona.ReviewPersonaSnapshot;

import java.util.List;
import java.util.Map;

public record AnalysisRun(
        String id,
        String mergeRequestId,
        List<String> personaIds,
        String invokedBy,
        String status,
        Map<String, PersonaReviewResult> results,
        Long pipelineId,
        String failureDetail,
        List<ReviewPersonaSnapshot> reviewPersonas,
        String requestFingerprint,
        long createdAt,
        long updatedAt,
        ExecutionOptions execution
) implements Id {
    public AnalysisRun {
        personaIds = personaIds == null ? List.of() : List.copyOf(personaIds);
        results = results == null ? Map.of() : Map.copyOf(results);
        reviewPersonas = reviewPersonas == null ? List.of() : List.copyOf(reviewPersonas);
    }

    public AnalysisRun(String id, String mergeRequestId, List<String> personaIds, String invokedBy,
                       String status, Map<String, PersonaReviewResult> results, Long pipelineId, String failureDetail,
                       List<ReviewPersonaSnapshot> reviewPersonas, String requestFingerprint, long createdAt, long updatedAt) {
        this(id, mergeRequestId, personaIds, invokedBy, status, results, pipelineId, failureDetail,
                reviewPersonas, requestFingerprint, createdAt, updatedAt, null);
    }

    public AnalysisRun(final String id, final String mergeRequestId, final List<String> personaIds,
                       final String invokedBy, final String status, final Map<String, PersonaReviewResult> results,
                       final long createdAt, final long updatedAt) {
        this(id, mergeRequestId, personaIds, invokedBy, status, results, null, null, List.of(), null, createdAt,
                updatedAt);
    }
}
