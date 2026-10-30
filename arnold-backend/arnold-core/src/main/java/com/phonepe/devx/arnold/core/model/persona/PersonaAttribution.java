package com.phonepe.devx.arnold.core.model.persona;

/**
 * Review persona that produced an Arnold comment, parsed from the note's
 * {@code <!-- arnold-persona:{runId}:{personaId}@{version} -->} marker.
 */
public record PersonaAttribution(String runId, String personaId, long version) {
}
