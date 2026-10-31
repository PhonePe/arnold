package com.phonepe.devx.arnold.models.mergerequest;

import com.phonepe.devx.arnold.models.analysis.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MergeRequestModelsCoverageTest {
    private static final String EXECUTION_ID = "123e4567-e89b-42d3-a456-426614174000";

    private static SmartDiffGroup group() {
        final var step = SmartDiffStep.builder().id("s").title("t").explanation("e")
                .changes(List.of()).build();
        return SmartDiffGroup.builder().id("g").title("t").rationale("r").steps(List.of(step)).build();
    }

    private static SmartDiffResultRequest.SmartDiffResultRequestBuilder request(final String status) {
        return SmartDiffResultRequest.builder().executionId(EXECUTION_ID).headSha("a".repeat(40)).status(status);
    }

    @Test
    void testIsLifecycleValid_shouldAcceptCompleted_whenSummaryAndGroupsPresent() {
        assertTrue(request("completed").groups(List.of(group())).summary("s").build().isLifecycleValid());
        assertFalse(request("completed").groups(List.of()).summary("s").build().isLifecycleValid());
        assertFalse(request("completed").groups(List.of(group())).summary("s").errorMessage("x").build()
                .isLifecycleValid());
    }

    @Test
    void testIsLifecycleValid_shouldRequireError_whenFailed() {
        assertTrue(request("failed").groups(List.of()).errorMessage("boom").build().isLifecycleValid());
        assertFalse(request("failed").groups(List.of()).errorMessage(" ").build().isLifecycleValid());
        assertFalse(request("failed").groups(null).errorMessage("boom").build().isLifecycleValid());
    }

    @Test
    void testIsLifecycleValid_shouldRequireEmptyPayload_whenRunning() {
        assertTrue(request("running").groups(List.of()).build().isLifecycleValid());
        assertFalse(request("running").groups(List.of()).summary("s").build().isLifecycleValid());
    }

    @Test
    void testIsLifecycleValid_shouldRequireCount_whenAssessmentCompleted() {
        final var base = request("running").groups(List.of()).commentAssessmentStatus("completed");

        assertFalse(base.build().isLifecycleValid());
        assertTrue(base.assessedCommentCount(3).build().isLifecycleValid());
        assertNotNull(base.usage(new TokenUsage(1, 1, List.of())).build().getUsage());
    }

    @Test
    void testSmartDiffResponse_shouldChainDefaults_whenUsingShortConstructors() {
        final var five = new SmartDiffResponse("sha", "completed", List.of(), 1L, null);
        final var eight = new SmartDiffResponse("sha", "completed", "s", List.of(), 1L, null, "queued", 2);

        assertEquals("not_requested", five.commentAssessmentStatus());
        assertNull(five.pipelineId());
        assertEquals(2, eight.assessedCommentCount());
        assertNull(eight.usage());
    }

    @Test
    void testViewedFilesResponse_shouldDefaultNulls_whenCollectionsMissing() {
        final var empty = new ViewedFilesResponse("sha", null, null, null, 1L);
        final var filled = new ViewedFilesResponse("sha", List.of("a"), List.of("s"), Map.of("k", "v"), 1L);

        assertTrue((empty.filePaths()).isEmpty());
        assertTrue((empty.storyStepIds()).isEmpty());
        assertTrue((empty.focusAreaFeedback()).isEmpty());
        assertEquals("v", (filled.focusAreaFeedback()).get("k"));
    }
}
