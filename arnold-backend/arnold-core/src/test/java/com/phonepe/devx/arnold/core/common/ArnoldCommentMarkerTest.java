package com.phonepe.devx.arnold.core.common;

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
}
