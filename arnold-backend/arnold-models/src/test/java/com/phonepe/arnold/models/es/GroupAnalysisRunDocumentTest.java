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

package com.phonepe.arnold.models.es;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupAnalysisRunDocumentTest {

    @Test
    void testGroupAnalysisRunDocument_shouldExposeComponents_whenBuilt() {
        final var document = new GroupAnalysisRunDocument("run-1", GroupAnalysisRunDocument.DOCUMENT_TYPE, "group-1",
                "Payments", "completed", "alice", 2, List.of("security"), 3, 1, 1, 77L, 1_000, 4_000, 3_000, 120L);

        assertEquals("group_analysis_run", document.type());
        assertEquals(List.of("security"), document.reviewPersonaIds());
        assertEquals(120L, document.totalTokens());
    }
}
