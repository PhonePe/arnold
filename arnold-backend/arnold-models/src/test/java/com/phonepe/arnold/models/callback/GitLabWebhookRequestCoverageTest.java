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

package com.phonepe.arnold.models.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitLabWebhookRequestCoverageTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testDeserialize_shouldMapFieldsAndNullifyJsonNullChanges_whenPayloadHasUnknownFields() throws Exception {
        final var request = mapper.readValue("""
                {"object_kind":"merge_request","unknown":1,
                 "project":{"id":1,"path_with_namespace":"g/p"},
                 "user":{"id":2,"username":"u","name":"U","state":"active"},
                 "object_attributes":{"id":3,"iid":4,"work_in_progress":true,"last_commit":{"id":"abc"},
                   "source_branch":"s","merge_status":"can_be_merged"},
                 "merge_request":{"id":3,"iid":4,"target_project_id":1},
                 "labels":[{"title":"l"}],"assignees":[],"changes":null}
                """, GitLabWebhookRequest.class);

        assertEquals("merge_request", request.objectKind());
        assertEquals("g/p", request.project().pathWithNamespace());
        assertEquals("u", request.user().username());
        assertTrue(request.attributes().draft());
        assertEquals("abc", request.attributes().lastCommit().id());
        assertEquals("can_be_merged", request.attributes().mergeStatus());
        assertEquals(1L, request.mergeRequest().targetProjectId());
        assertEquals("l", request.labels().get(0).title());
        assertNull(request.changes());
    }

    @Test
    void testDeserialize_shouldKeepChanges_whenObjectProvided() throws Exception {
        final var request = mapper.readValue("{\"changes\":{\"title\":{}}}", GitLabWebhookRequest.class);

        assertTrue(request.changes().has("title"));
    }
}
