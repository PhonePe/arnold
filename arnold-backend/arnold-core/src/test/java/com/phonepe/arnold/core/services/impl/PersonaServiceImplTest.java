package com.phonepe.arnold.core.services.impl;

import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.model.persona.PersonaRecord;
import com.phonepe.arnold.core.storage.aerospike.AerospikePersonaStore;
import com.phonepe.arnold.models.persona.CreatePersonaRequest;
import com.phonepe.arnold.models.persona.PersonaPolicy;
import com.phonepe.arnold.models.persona.PersonaPrerequisite;
import com.phonepe.arnold.models.persona.PersonaRule;
import com.phonepe.arnold.models.persona.PersonaStatus;
import com.phonepe.arnold.models.persona.UpdatePersonaRequest;
import com.livetheoogway.crudstore.core.Versioned;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonaServiceImplTest {
    private final AerospikePersonaStore store = mock(AerospikePersonaStore.class);
    private final PersonaServiceImpl service = new PersonaServiceImpl(store);

    @Test
    void testList_shouldReturnApprovedBuiltIns_whenCatalogIsEmpty() {
        when(store.list()).thenReturn(List.of());

        final var personas = service.list("user-1");

        assertEquals(List.of("netra", "hayai", "kozo", "shinrai"),
                personas.stream().map(persona -> persona.id()).toList());
        assertTrue(personas.stream().allMatch(persona -> persona.status() == PersonaStatus.PUBLISHED));
    }

    @Test
    void testPersonaRecord_shouldDefaultContributorToOwner_whenStoredRecordPredatesContributors() {
        final var persona = new PersonaRecord("persona-1", "API Guardian", "Checks APIs", "", null, List.of(),
                "user-1", null, PersonaStatus.DRAFT, 1, 10, 20);

        assertEquals(List.of("user-1"), persona.contributorIds());
    }

    @Test
    void testCreate_shouldPersistNormalizedDraft_whenRequestIsValid() {
        final var request = CreatePersonaRequest.builder()
                .name("  API Guardian ")
                .summary(" Checks API compatibility ")
                .policy(new PersonaPolicy(1, " Review public contracts. ",
                        List.of(new PersonaRule("api.compatibility", " Preserve existing response fields. ")),
                        List.of(new PersonaPrerequisite("api.registration", " An endpoint changes. ",
                                " Update the API registry. ")), List.of(" Ignore generated clients. ")))
                .tags(List.of("API", " api ", "Contracts"))
                .contributorIds(List.of("user-2", " user-2 "))
                .build();

        final var response = service.create("user-1", request);

        final var persona = ArgumentCaptor.forClass(PersonaRecord.class);
        verify(store).create(persona.capture());
        assertEquals(List.of("api", "contracts"), persona.getValue().tags());
        assertEquals(List.of("user-1", "user-2"), persona.getValue().contributorIds());
        assertEquals(PersonaStatus.DRAFT, response.status());
        assertEquals("API Guardian", response.name());
        assertEquals("Review public contracts.", persona.getValue().policy().guidance());
        assertEquals("""
                version: 1

                guidance: |-
                  Review public contracts.

                rules:
                  - id: api.compatibility
                    requirement: |-
                      Preserve existing response fields.

                prerequisites:
                  - id: api.registration
                    applies_when: |-
                      An endpoint changes.
                    requirement: |-
                      Update the API registry.

                ignore:
                  - |-
                    Ignore generated clients.
                """, response.instructions());
    }

    @Test
    void testCreateAndUpdate_shouldAcceptAllContributors_whenMoreThanTwentyAreProvided() {
        final var contributors = java.util.stream.IntStream.rangeClosed(1, 50)
                .mapToObj(index -> "user-" + index).toList();
        final var policy = new PersonaPolicy(1, "Review contracts.", List.of(), List.of(), List.of());
        final var create = CreatePersonaRequest.builder().name("API Guardian").summary("Checks APIs")
                .policy(policy).contributorIds(contributors).build();
        final var update = UpdatePersonaRequest.builder().name("API Guardian").summary("Checks APIs")
                .policy(policy).contributorIds(contributors).version(1).build();
        try (final var factory = javax.validation.Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(create).isEmpty());
            assertTrue(factory.getValidator().validate(update).isEmpty());
        }

        assertEquals(contributors, service.create("user-1", create).contributorIds());
        final var stored = new PersonaRecord("persona-1", "API Guardian", "Checks APIs", "", policy,
                List.of(), "user-1", List.of("user-1"), PersonaStatus.DRAFT, 1, 10, 20);
        when(store.getWithVersion("persona-1")).thenReturn(Optional.of(Versioned.of(stored, 7L)));
        assertEquals(contributors, service.update("user-1", "persona-1", update).contributorIds());
    }

    @Test
    void testCreate_shouldRejectPolicy_whenRuleIdsAreDuplicated() {
        final var request = CreatePersonaRequest.builder()
                .name("API Guardian")
                .summary("Checks API compatibility")
                .policy(new PersonaPolicy(1, "", List.of(new PersonaRule("api.contract", "Preserve fields.")),
                        List.of(new PersonaPrerequisite("api.contract", "An endpoint changes.",
                                "Update the registry.")), List.of()))
                .build();

        assertThrows(ArnoldError.class, () -> service.create("user-1", request));
    }

    @Test
    void testUpdate_shouldAllowContributorToManagePersona_whenContributorIsNotOwner() {
        final var policy = new PersonaPolicy(1, "Review contracts.", List.of(), List.of(), List.of());
        final var stored = new PersonaRecord("persona-1", "API Guardian", "Checks APIs", "", policy,
                List.of("api"), "user-1", List.of("user-1", "user-2"), PersonaStatus.DRAFT, 3, 10, 20);
        when(store.getWithVersion("persona-1")).thenReturn(Optional.of(Versioned.of(stored, 7L)));
        final var request = UpdatePersonaRequest.builder().name("API Guardian").summary("Checks APIs")
                .policy(policy).tags(List.of("api")).contributorIds(List.of("user-2", "user-3")).version(3).build();

        final var response = service.update("user-2", "persona-1", request);

        verify(store).checkAndUpdate(any(PersonaRecord.class), eq(7L));
        assertEquals(List.of("user-2", "user-3"), response.contributorIds());
        assertEquals("user-1", response.ownerId());
    }
}
