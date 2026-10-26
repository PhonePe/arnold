package com.phonepe.devx.arnold.core.common;

import lombok.experimental.UtilityClass;

/**
 * Hidden HTML comment appended to every note Arnold writes to GitLab, so review feedback ingested later can be
 * attributed to Arnold rather than the previous AI review workflow. GitLab does not render HTML comments.
 */
@UtilityClass
public class ArnoldCommentMarker {
    public static final String MARKER = "<!-- arnold -->";
    private static final String SUFFIX = "\n\n" + MARKER;

    public String tag(final String body) {
        if (body == null || body.isBlank() || isTagged(body)) {
            return body;
        }
        return body + SUFFIX;
    }

    public String strip(final String body) {
        if (body == null) {
            return null;
        }
        if (body.endsWith(SUFFIX)) {
            return body.substring(0, body.length() - SUFFIX.length());
        }
        return body.endsWith(MARKER) ? body.substring(0, body.length() - MARKER.length()) : body;
    }

    public boolean isTagged(final String body) {
        return body != null && body.contains(MARKER);
    }
}
