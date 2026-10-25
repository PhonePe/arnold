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

package com.phonepe.arnold.models.mergerequest;

import java.util.List;

public record SmartDiffResponse(String headSha, String status, String summary, List<SmartDiffGroup> groups,
                                long updatedAt, String errorMessage, String commentAssessmentStatus,
                                int assessedCommentCount) {
    public SmartDiffResponse(final String headSha, final String status, final String summary,
                             final List<SmartDiffGroup> groups, final long updatedAt, final String errorMessage) {
        this(headSha, status, summary, groups, updatedAt, errorMessage, "not_requested", 0);
    }

    public SmartDiffResponse(final String headSha, final String status, final List<SmartDiffGroup> groups,
                             final long updatedAt, final String errorMessage) {
        this(headSha, status, null, groups, updatedAt, errorMessage, "not_requested", 0);
    }
}
