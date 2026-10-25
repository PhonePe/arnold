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

package com.phonepe.arnold.models.es;

import java.util.List;

/**
 * One persona-review run. {@code personaVersions} holds {@code personaId@version} so adoption and
 * effectiveness can be bucketed per persona revision.
 */
public record AnalysisRunDocument(
        String id,
        String type,
        String mergeRequestId,
        String provider,
        String repositoryId,
        long number,
        String status,
        String outcome,
        String invokedBy,
        List<String> personaIds,
        List<String> personaVersions,
        int personaCount,
        int succeededPersonas,
        int failedPersonas,
        int findingCount,
        int postedFindings,
        int failedFindings,
        String harness,
        String model,
        Long pipelineId,
        String failureDetail,
        long createdAt,
        long updatedAt,
        long durationMs
) {
    public static final String TYPE = "analysis_run";
}
