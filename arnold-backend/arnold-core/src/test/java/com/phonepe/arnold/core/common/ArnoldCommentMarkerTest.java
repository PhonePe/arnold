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

package com.phonepe.arnold.core.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArnoldCommentMarkerTest {

    @Test
    void testTag_shouldAppendMarkerOnce() {
        final var tagged = ArnoldCommentMarker.tag("Looks good");

        assertEquals("Looks good\n\n<!-- arnold -->", tagged);
        assertEquals(tagged, ArnoldCommentMarker.tag(tagged));
        assertTrue(ArnoldCommentMarker.isTagged(tagged));
    }

    @Test
    void testTag_shouldLeaveBlankBodiesUntouched() {
        assertNull(ArnoldCommentMarker.tag(null));
        assertEquals("  ", ArnoldCommentMarker.tag("  "));
        assertFalse(ArnoldCommentMarker.isTagged(null));
    }

    @Test
    void testStrip_shouldRemoveTrailingMarker() {
        assertEquals("Looks good", ArnoldCommentMarker.strip("Looks good\n\n<!-- arnold -->"));
        assertEquals("Looks good", ArnoldCommentMarker.strip("Looks good<!-- arnold -->"));
        assertEquals("Looks good", ArnoldCommentMarker.strip("Looks good"));
        assertNull(ArnoldCommentMarker.strip(null));
    }

    @Test
    void testPersona_shouldParseAndStripPersonaMarker() {
        final var body = "Finding\n\n<!-- arnold-persona:run-1:netra@3 -->\n\n<!-- arnold -->";

        final var persona = ArnoldCommentMarker.persona(body).orElseThrow();

        assertEquals("run-1", persona.runId());
        assertEquals("netra", persona.personaId());
        assertEquals(3, persona.version());
        assertTrue(ArnoldCommentMarker.isTagged("Finding\n\n<!-- arnold-persona:run-1:netra@3 -->"));
        assertEquals("Finding", ArnoldCommentMarker.strip(body));
        assertTrue(ArnoldCommentMarker.persona("Finding\n\n<!-- arnold -->").isEmpty());
        assertTrue(ArnoldCommentMarker.persona("<!-- arnold-persona:run-1:netra@x -->").isEmpty());
    }

    @Test
    void recognisesClioGroupReviewMarker() {
        final var body = "Finding\n\n<!-- arnold-group:run-1:f-2 -->";
        assertTrue(ArnoldCommentMarker.isTagged(body));
        assertFalse(ArnoldCommentMarker.isTagged("Finding\n\n<!-- clio-meta:{} -->"));
        assertEquals(body, ArnoldCommentMarker.tag(body));
    }
}
