package com.phonepe.devx.arnold.models.request;

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
