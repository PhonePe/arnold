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

package com.phonepe.arnold.core.services;

import com.phonepe.arnold.models.analysis.AnalysisResponse;
import com.phonepe.arnold.models.analysis.PersonaCompletionRequest;
import com.phonepe.arnold.models.analysis.ReviewPersonaDefinition;
import com.phonepe.arnold.models.analysis.StartAnalysisRequest;

import java.util.List;

public interface AnalysisService {
    AnalysisResponse start(StartAnalysisRequest request, String userId);

    AnalysisResponse get(String runId);

    List<AnalysisResponse> list(String mergeRequestId, Long before, int limit);

    AnalysisResponse completePersona(PersonaCompletionRequest request);

    /**
     * Persona definitions frozen on a running analysis. {@code refs} ({@code id@version,...}), when given, must match
     * the run's snapshot exactly so a pipeline from a superseded dispatch cannot pick up different personas.
     */
    List<ReviewPersonaDefinition> reviewPersonas(String runId, String refs);
}
