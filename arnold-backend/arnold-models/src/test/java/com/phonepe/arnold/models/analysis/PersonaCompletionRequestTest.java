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

package com.phonepe.arnold.models.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersonaCompletionRequestTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testDeserialization_shouldDefaultFindings_whenLegacyCallbackOmitsThem() throws Exception {
        final var request = MAPPER.readValue("""
                {
                  "mergeRequestId": "gitlab:42:12",
                  "runId": "run-1",
                  "personaId": "improve",
                  "outcome": "SUCCEEDED",
                  "detail": "0 inline suggestions posted"
                }
                """, PersonaCompletionRequest.class);

        assertEquals(List.of(), request.getFindings());
    }

    @Test
    void testSerialization_shouldPreserveFindings_whenCommentsWerePublished() throws Exception {
        final var finding = new AnalysisFindingResponse("101", "head", "src/Main.java", "src/Main.java",
                12, "Fix this", "posted", "https://gitlab.example/project/-/merge_requests/12#note_101");
        final var request = PersonaCompletionRequest.builder()
                .mergeRequestId("gitlab:42:12")
                .runId("run-1")
                .personaId("improve")
                .outcome(PersonaCompletionRequest.Outcome.SUCCEEDED)
                .detail("1 inline suggestion posted")
                .findings(List.of(finding))
                .build();

        final var deserialized = MAPPER.readValue(MAPPER.writeValueAsBytes(request), PersonaCompletionRequest.class);

        assertEquals(List.of(finding), deserialized.getFindings());
    }
}
