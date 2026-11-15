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

import com.phonepe.arnold.core.model.persona.PersonaAttribution;
import lombok.experimental.UtilityClass;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Hidden HTML comment appended to every note Arnold writes to GitLab, so review feedback ingested later can be
 * attributed to Arnold rather than the previous AI review workflow. GitLab does not render HTML comments.
 */
@UtilityClass
public class ArnoldCommentMarker {
    public static final String MARKER = "<!-- arnold -->";
    // Clio's group_review flow (always Arnold-triggered) tags its notes with <!-- arnold-group:{runId}:{findingId} -->.
    public static final String GROUP_MARKER_PREFIX = "<!-- arnold-group:";
    // Clio's Improve flow tags persona findings with <!-- arnold-persona:{runId}:{personaId}@{version} -->.
    public static final String PERSONA_MARKER_PREFIX = "<!-- arnold-persona:";
    private static final String SUFFIX = "\n\n" + MARKER;
    private static final Pattern PERSONA_MARKER = Pattern.compile(
            "(?:\\n\\n)?<!-- arnold-persona:([^:\\s]+):([A-Za-z0-9][A-Za-z0-9._-]{0,127})@(\\d{1,18}) -->");

    public String tag(final String body) {
        if (body == null || body.isBlank() || isTagged(body)) {
            return body;
        }
        return body + SUFFIX;
    }

    public String strip(final String raw) {
        if (raw == null) {
            return null;
        }
        final var body = PERSONA_MARKER.matcher(raw).replaceAll("");
        if (body.endsWith(SUFFIX)) {
            return body.substring(0, body.length() - SUFFIX.length());
        }
        return body.endsWith(MARKER) ? body.substring(0, body.length() - MARKER.length()) : body;
    }

    public boolean isTagged(final String body) {
        return body != null && (body.contains(MARKER) || body.contains(GROUP_MARKER_PREFIX)
                || body.contains(PERSONA_MARKER_PREFIX));
    }

    public Optional<PersonaAttribution> persona(final String body) {
        if (body == null) {
            return Optional.empty();
        }
        final var matcher = PERSONA_MARKER.matcher(body);
        return matcher.find()
                ? Optional.of(new PersonaAttribution(matcher.group(1), matcher.group(2), Long.parseLong(matcher.group(3))))
                : Optional.empty();
    }
}
