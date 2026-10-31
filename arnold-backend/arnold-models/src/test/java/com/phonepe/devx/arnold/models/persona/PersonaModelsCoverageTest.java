package com.phonepe.devx.arnold.models.persona;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PersonaModelsCoverageTest {

    @Test
    void testPersonaPolicy_shouldDefaultNullLists_whenMissing() {
        final var policy = new PersonaPolicy(1, "g", null, null, null);
        final var full = new PersonaPolicy(2, "g", List.of(new PersonaRule("r", "req")),
                List.of(new PersonaPrerequisite("p", "when", "req")), List.of("*.md"));

        assertTrue((policy.rules()).isEmpty());
        assertTrue((policy.prerequisites()).isEmpty());
        assertTrue((policy.ignore()).isEmpty());
        assertEquals(1, (full.rules()).size());
        assertEquals(java.util.List.of("*.md"), (full.ignore()));
    }

    @Test
    void testPersonaResponse_shouldDefaultNullLists_whenMissing() {
        final var response = new PersonaResponse("id", "n", "s", "i", null, null, "o", null, PersonaStatus.DRAFT,
                true, 1, 2, 3);

        assertTrue((response.tags()).isEmpty());
        assertTrue((response.contributorIds()).isEmpty());
        assertEquals(PersonaStatus.DRAFT, response.status());
        assertEquals(PersonaStatus.values()[1], PersonaStatus.valueOf("PUBLISHED"));
    }

    @Test
    void testPersonaMetricsResponse_shouldExposeComponents_whenBuilt() {
        final var response = new PersonaMetricsResponse("p", 1, 2, 3, new PersonaMetricsResponse.Findings(4, 3, 1),
                new PersonaMetricsResponse.Acceptance(5, 2, 4, 50.0),
                new PersonaMetricsResponse.Resolution(6, 3, 2, 66.6),
                List.of(new PersonaMetricsResponse.Week("w", 1, 2, 3, null)),
                List.of(new PersonaMetricsResponse.Version(1, 2, 3, 4, 5, 6.0)));

        assertEquals(1, response.findings().failed());
        assertEquals(50.0, response.acceptance().acceptanceRate());
        assertEquals(2, response.resolution().resolved());
        assertNull(response.weekly().get(0).acceptanceRate());
        assertEquals(1, response.versions().get(0).version());
    }
}
