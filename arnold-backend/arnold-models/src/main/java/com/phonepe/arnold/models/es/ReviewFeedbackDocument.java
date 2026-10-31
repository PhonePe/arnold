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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.phonepe.arnold.models.scm.User;
import com.phonepe.arnold.models.request.AcceptanceStatus;
import com.phonepe.arnold.models.request.CommentType;
import com.phonepe.arnold.models.request.Priority;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder(toBuilder = true)
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewFeedbackDocument {
    public static final String ORIGIN_ARNOLD = "arnold";
    public static final String ORIGIN_WORKFLOW = "workflow";


    Long mrId;
    Long mrIid;
    Long mrAuthorId;
    String assessedSha;
    User mrAuthor;
    ProjectInfo project;
    Long noteId;
    String discussionId;
    String noteCreatedAt;
    String comment;
    Position position;
    User reviewer;
    CommentType commentType;
    Boolean hasSuggestion;
    AcceptanceStatus acceptanceStatus;
    Double score;
    String reasoning;
    Priority priority;
    String commentCategory;
    String sourceHarness;
    String sourceModel;
    String analysisRunId;
    String personaId;
    Long personaVersion;
    /** {@link #ORIGIN_ARNOLD} when the comment came from an Arnold run or was posted through Arnold. */
    String origin;
    Long ingestedAt;

    /**
     * Deterministic document ID for ES upsert: projectId:mrId:noteId
     */
    public String documentId() {
        final Long projectId = project != null ? project.getProjectId() : null;
        return projectId + ":" + mrId + ":" + noteId;
    }
}
