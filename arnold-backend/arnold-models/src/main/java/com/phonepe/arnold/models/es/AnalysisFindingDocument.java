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

/**
 * One finding produced by an analysis run, stored alongside {@link AnalysisRunDocument}. {@code personaId} is null for
 * findings from the general Improve pass.
 */
public record AnalysisFindingDocument(
        String id,
        String type,
        String runId,
        String mergeRequestId,
        String provider,
        String repositoryId,
        long number,
        String invokedBy,
        String personaId,
        Long personaVersion,
        String publicationStatus,
        String filePath,
        long createdAt
) {
    public static final String DOCUMENT_TYPE = "analysis_finding";

    public static String id(final String runId, final String findingId) {
        return "finding:" + runId + ":" + findingId;
    }
}
