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
 * One analysis run across a merge request group, as opposed to a single-MR {@link AnalysisRunDocument}.
 */
public record GroupAnalysisRunDocument(
        String id,
        String type,
        String groupId,
        String groupName,
        String status,
        String invokedBy,
        int memberCount,
        List<String> reviewPersonaIds,
        int findingCount,
        int postedFindings,
        int failedFindings,
        Long pipelineId,
        long createdAt,
        long updatedAt,
        long durationMs,
        Long totalTokens
) {
    public static final String DOCUMENT_TYPE = "group_analysis_run";
}
