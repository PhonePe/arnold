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

package com.phonepe.arnold.models.comment;

import com.phonepe.arnold.models.request.AcceptanceStatus;

import java.util.List;

public record CommentResponse(
        String id,
        String mergeRequestId,
        String discussionId,
        long noteId,
        Long draftId,
        Long authorId,
        String authorUsername,
        String body,
        String state,
        boolean resolvable,
        boolean resolved,
        boolean individualNote,
        boolean deleted,
        String source,
        long createdAt,
        long gitlabUpdatedAt,
        long lastSyncedAt,
        AcceptanceStatus acceptanceStatus,
        String assessedSha,
        String assessmentReasoning,
        DiscussionPosition position,
        List<SuggestionResponse> suggestions,
        String analysisRunId,
        String personaId,
        Long personaVersion,
        String personaName,
        String reviewSource
) {
    /** Persona-attributed AI comment. */
    public static final String SOURCE_PERSONA = "persona";
    /** AI comment from the general review pass (no persona). */
    public static final String SOURCE_AI = "ai";
    public static final String SOURCE_HUMAN = "human";
}
