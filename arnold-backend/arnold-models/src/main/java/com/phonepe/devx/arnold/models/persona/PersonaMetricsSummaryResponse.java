package com.phonepe.devx.arnold.models.persona;

import java.util.List;

/**
 * Per-persona headline metrics over {@code [from, to)}. {@code general} covers Arnold findings and feedback not
 * attributed to any persona, for comparison.
 */
public record PersonaMetricsSummaryResponse(long from, long to, List<Entry> personas, Entry general) {

    public record Entry(String personaId, long runs, long findings, long posted, long feedback, long accepted,
                        Double acceptanceRate) {
    }
}
