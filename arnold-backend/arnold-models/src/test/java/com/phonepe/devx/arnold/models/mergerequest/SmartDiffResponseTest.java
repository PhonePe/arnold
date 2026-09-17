package com.phonepe.devx.arnold.models.mergerequest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmartDiffResponseTest {
    private final ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Test
    void testSerialization_shouldMatchFrontendContract_whenResponseIsCompleted() throws Exception {
        final var change = SmartDiffChange.builder().filePath("src/A.java").oldStartLine(10).oldEndLine(14)
                .newStartLine(12).newEndLine(18).build();
        final var step = SmartDiffStep.builder().id("s1").title("API").explanation("Review it")
                .changes(List.of(change)).build();
        final var group = SmartDiffGroup.builder().id("g1").title("Contract").rationale("Start here")
                .steps(List.of(step)).build();
        final var response = new SmartDiffResponse("a".repeat(40), "completed", "Review the API first.",
                List.of(group), 123L, null);
        final var expected = mapper.readTree("""
                {"headSha":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","status":"completed",
                "summary":"Review the API first.","groups":[{
                "id":"g1","title":"Contract","rationale":"Start here","steps":[{"id":"s1","title":"API",
                "explanation":"Review it","changes":[{"filePath":"src/A.java","oldStartLine":10,
                "oldEndLine":14,"newStartLine":12,"newEndLine":18}]}]}],"updatedAt":123,
                "commentAssessmentStatus":"not_requested","assessedCommentCount":0}
                """);

        assertEquals(expected.toString(), mapper.writeValueAsString(response));
    }
}
