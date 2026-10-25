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
