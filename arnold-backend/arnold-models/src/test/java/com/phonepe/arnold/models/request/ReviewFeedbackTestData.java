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

package com.phonepe.arnold.models.request;

import java.math.BigDecimal;
import java.util.List;

/**
 * Shared test data builders for review feedback request models.
 */
public final class ReviewFeedbackTestData {

    private ReviewFeedbackTestData() {
    }

    public static ReviewComment buildComment() {
        return buildComment(1L);
    }

    public static ReviewComment buildComment(Long noteId) {
        return ReviewComment.builder()
                .noteId(noteId)
                .discussionId("disc-" + noteId)
                .commentType(CommentType.INLINE)
                .hasSuggestion(true)
                .status(AcceptanceStatus.APPLIED)
                .score(new BigDecimal("0.85"))
                .reasoning("Good catch")
                .priority(Priority.HIGH)
                .commentCategory("bug")
                .sourceHarness("codex")
                .sourceModel("gpt-5.6-sol")
                .build();
    }

    public static ReviewMetadata buildMetadata() {
        return ReviewMetadata.builder()
                .projectId(100L)
                .mrId(200L)
                .mrIid(25L)
                .mrAuthorId(300L)
                .build();
    }

    public static ReviewFeedbackRequest buildRequest(List<ReviewComment> comments) {
        return ReviewFeedbackRequest.builder()
                .metadata(buildMetadata())
                .comments(comments)
                .build();
    }
}
