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

package com.phonepe.arnold.models.mergerequest;

import com.phonepe.arnold.models.analysis.TokenUsage;

import java.util.List;

public record SmartDiffResponse(String headSha, String status, String summary, List<SmartDiffGroup> groups,
                                long updatedAt, String errorMessage, String commentAssessmentStatus,
                                int assessedCommentCount, TokenUsage usage, Long pipelineId,
                                String pipelineUrl) {
    public SmartDiffResponse(final String headSha, final String status, final String summary,
                             final List<SmartDiffGroup> groups, final long updatedAt, final String errorMessage,
                             final String commentAssessmentStatus, final int assessedCommentCount,
                             final TokenUsage usage) {
        this(headSha, status, summary, groups, updatedAt, errorMessage, commentAssessmentStatus, assessedCommentCount,
                usage, null, null);
    }

    public SmartDiffResponse(final String headSha, final String status, final String summary,
                             final List<SmartDiffGroup> groups, final long updatedAt, final String errorMessage,
                             final String commentAssessmentStatus, final int assessedCommentCount) {
        this(headSha, status, summary, groups, updatedAt, errorMessage, commentAssessmentStatus, assessedCommentCount,
                null);
    }

    public SmartDiffResponse(final String headSha, final String status, final String summary,
                             final List<SmartDiffGroup> groups, final long updatedAt, final String errorMessage) {
        this(headSha, status, summary, groups, updatedAt, errorMessage, "not_requested", 0, null);
    }

    public SmartDiffResponse(final String headSha, final String status, final List<SmartDiffGroup> groups,
                             final long updatedAt, final String errorMessage) {
        this(headSha, status, null, groups, updatedAt, errorMessage, "not_requested", 0, null);
    }
}
