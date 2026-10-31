package com.phonepe.devx.arnold.core.config;

import lombok.Data;

@Data
public class AnalysisConfiguration {
    /**
     * How review personas reach the Clio Improve pipeline: {@code INLINE} sends full definitions as
     * {@code REVIEW_PERSONAS_JSON}; {@code FETCH} sends only {@code REVIEW_PERSONA_REFS} ({@code id@version,...}) and
     * Clio fetches the definitions from {@code GET /analysis-callbacks/v1/runs/{runId}/personas}.
     */
    private PersonaDelivery personaDelivery = PersonaDelivery.INLINE;

    public enum PersonaDelivery {
        INLINE,
        FETCH
    }
}
