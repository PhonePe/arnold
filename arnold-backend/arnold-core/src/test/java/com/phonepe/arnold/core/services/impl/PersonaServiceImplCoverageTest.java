/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.persona.PersonaRecord;
import com.phonepe.arnold.core.storage.aerospike.AerospikePersonaStore;
import com.phonepe.arnold.models.persona.CreatePersonaRequest;
import com.phonepe.arnold.models.persona.PersonaPolicy;
import com.phonepe.arnold.models.persona.PersonaRule;
import com.phonepe.arnold.models.persona.PersonaStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonaServiceImplCoverageTest {
    private static final PersonaPolicy POLICY = new PersonaPolicy(1, "Review contracts.", List.of(), List.of(),
            List.of());

    private final AerospikePersonaStore store = mock(AerospikePersonaStore.class);
    private final PersonaServiceImpl service = new PersonaServiceImpl(store);

    private static PersonaRecord persona(final String id, final PersonaStatus status, final long version,
                                         final long updatedAt, final String... contributors) {
        return new PersonaRecord(id, "Name " + id, "Summary", "", POLICY, List.of("api"), "owner",
                List.of(contributors), status, version, 1, updatedAt);
    }

    private static void assertCode(final ArnoldErrorCode code, final Runnable action) {
        assertEquals(code, assertThrows(ArnoldError.class, action::run).getErrorCode());
    }

    @Test
    void testList_shouldHideOthersDraftsAndSortByRecency_whenCatalogMixed() {
        when(store.list()).thenReturn(List.of(
                persona("old", PersonaStatus.PUBLISHED, 1, 10, "owner"),
                persona("new", PersonaStatus.PUBLISHED, 1, 20, "owner"),
                persona("mine", PersonaStatus.DRAFT, 1, 30, "user-1"),
                persona("hidden", PersonaStatus.DRAFT, 1, 40, "owner")));

        final var ids = service.list("user-1").stream().map(response -> response.id()).toList();

        assertEquals(List.of("netra", "hayai", "kozo", "shinrai", "mine", "new", "old").size(), ids.size());
        assertEquals(List.of("mine", "new", "old"), ids.subList(4, 7));
    }

    @Test
    void testGet_shouldReturnBuiltIn_withoutHittingStore() {
        assertEquals("Netra", service.get("user-1", "netra").name());
        assertTrue(service.get("user-1", "hayai").systemOwned());
        verify(store, never()).get(any(String.class));
    }

    @Test
    void testGet_shouldHonorVisibility_whenPersonaIsCustom() {
        when(store.get("draft")).thenReturn(Optional.of(persona("draft", PersonaStatus.DRAFT, 2, 1, "owner")));
        when(store.get("live")).thenReturn(Optional.of(persona("live", PersonaStatus.PUBLISHED, 2, 1, "owner")));
        when(store.get("missing")).thenReturn(Optional.empty());

        assertEquals("draft", service.get("owner", "draft").id());
        assertEquals("live", service.get("stranger", "live").id());
        assertCode(ArnoldErrorCode.NOT_FOUND_ERROR, () -> service.get("stranger", "draft"));
        assertCode(ArnoldErrorCode.NOT_FOUND_ERROR, () -> service.get("stranger", "missing"));
    }

    @Test
    void testGet_shouldDeriveSingleGuidancePolicy_whenStoredRecordHasNoPolicy() {
        final var legacy = new PersonaRecord("legacy", "Legacy", "Old", "Check things.", null, List.of(), "owner",
                List.of("owner"), PersonaStatus.PUBLISHED, 1, 1, 1);
        when(store.get("legacy")).thenReturn(Optional.of(legacy));

        final var response = service.get("owner", "legacy");

        assertEquals("Check things.", response.policy().guidance());
        assertEquals("version: 1\n\nguidance: |-\n  Check things.\n", response.instructions());
    }

    @Test
    void testPublishAndUnpublish_shouldBumpVersionAndFlipStatus_whenVersionMatches() {
        final var stored = persona("p1", PersonaStatus.DRAFT, 3, 1, "user-1");
        when(store.getWithVersion("p1")).thenReturn(Optional.of(Versioned.of(stored, 9L)));

        final var published = service.publish("user-1", "p1", 3);
        final var unpublished = service.unpublish("user-1", "p1", 3);

        assertEquals(PersonaStatus.PUBLISHED, published.status());
        assertEquals(PersonaStatus.DRAFT, unpublished.status());
        assertEquals(4, published.version());
        verify(store, org.mockito.Mockito.times(2)).checkAndUpdate(any(PersonaRecord.class), anyLong());
    }

    @Test
    void testMutate_shouldFail_whenVersionStaleOrConcurrentUpdate() {
        final var stored = persona("p1", PersonaStatus.DRAFT, 3, 1, "user-1");
        when(store.getWithVersion("p1")).thenReturn(Optional.of(Versioned.of(stored, 9L)));

        assertCode(ArnoldErrorCode.CONFLICT_ERROR, () -> service.publish("user-1", "p1", 2));

        doThrow(new ConcurrentUpdateException("stale")).when(store).checkAndUpdate(any(PersonaRecord.class), anyLong());
        assertCode(ArnoldErrorCode.CONFLICT_ERROR, () -> service.publish("user-1", "p1", 3));
    }

    @Test
    void testManage_shouldRejectBuiltInMissingAndNonContributor() {
        when(store.getWithVersion("p1")).thenReturn(
                Optional.of(Versioned.of(persona("p1", PersonaStatus.DRAFT, 1, 1, "owner"), 1L)));
        when(store.getWithVersion("none")).thenReturn(Optional.empty());

        assertCode(ArnoldErrorCode.FORBIDDEN_ERROR, () -> service.publish("user-1", "netra", 1));
        assertCode(ArnoldErrorCode.NOT_FOUND_ERROR, () -> service.publish("user-1", "none", 1));
        assertCode(ArnoldErrorCode.FORBIDDEN_ERROR, () -> service.delete("intruder", "p1", 1));
    }

    @Test
    void testDelete_shouldRemoveOnlyMatchingDraft() {
        when(store.getWithVersion("draft")).thenReturn(
                Optional.of(Versioned.of(persona("draft", PersonaStatus.DRAFT, 2, 1, "user-1"), 1L)));
        when(store.getWithVersion("live")).thenReturn(
                Optional.of(Versioned.of(persona("live", PersonaStatus.PUBLISHED, 2, 1, "user-1"), 1L)));

        assertCode(ArnoldErrorCode.CONFLICT_ERROR, () -> service.delete("user-1", "draft", 1));
        assertCode(ArnoldErrorCode.OPERATION_NOT_ALLOWED, () -> service.delete("user-1", "live", 2));
        service.delete("user-1", "draft", 2);

        verify(store).delete("draft");
        verify(store, never()).delete("live");
    }

    @Test
    void testResolveForAnalysis_shouldSnapshotPersonas_whenIdsValid() {
        assertTrue(service.resolveForAnalysis("user-1", null).isEmpty());
        assertTrue(service.resolveForAnalysis("user-1", List.of()).isEmpty());

        final var snapshots = service.resolveForAnalysis("user-1", List.of("netra", "hayai"));

        assertEquals(List.of("netra", "hayai"), snapshots.stream().map(snapshot -> snapshot.id()).toList());
        assertEquals(1, snapshots.get(0).version());
    }

    @Test
    void testResolveForAnalysis_shouldRejectDuplicatesAndMoreThanThree() {
        assertCode(ArnoldErrorCode.WRONG_INPUT_ERROR,
                () -> service.resolveForAnalysis("user-1", List.of("netra", "netra")));
        assertCode(ArnoldErrorCode.WRONG_INPUT_ERROR,
                () -> service.resolveForAnalysis("user-1", List.of("netra", "hayai", "kozo", "shinrai")));
    }

    private ArnoldErrorCode createWith(final PersonaPolicy policy, final List<String> contributors) {
        final var request = CreatePersonaRequest.builder().name("N").summary("S").policy(policy)
                .contributorIds(contributors).build();
        return assertThrows(ArnoldError.class, () -> service.create("user-1", request)).getErrorCode();
    }

    @Test
    void testCreate_shouldRejectInvalidPolicies() {
        final var tooMany = IntStream.range(0, 21).mapToObj(index -> new PersonaRule("r" + index, "x")).toList();
        final var invalid = List.of(
                new PersonaPolicy(2, "g", List.of(), List.of(), List.of()),
                new PersonaPolicy(1, "", List.of(), List.of(), List.of()),
                new PersonaPolicy(1, "g".repeat(2_001), List.of(), List.of(), List.of()),
                new PersonaPolicy(1, "g", tooMany, List.of(), List.of()),
                new PersonaPolicy(1, "g", List.of(new PersonaRule("bad id", "x")), List.of(), List.of()),
                new PersonaPolicy(1, "g", List.of(new PersonaRule("r", " ")), List.of(), List.of()),
                new PersonaPolicy(1, "g", List.of(), List.of(), List.of(" ")));

        invalid.forEach(policy -> assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR, createWith(policy, null)));
        assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR, createWith(null, null));
        assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR, createWith(POLICY, List.of(" ")));
    }

    @Test
    void testCreate_shouldRejectPolicy_whenRenderedYamlExceedsLimit() {
        final var rules = IntStream.range(0, 20).mapToObj(index -> new PersonaRule("r" + index, "x".repeat(450)))
                .toList();

        assertEquals(ArnoldErrorCode.WRONG_INPUT_ERROR,
                createWith(new PersonaPolicy(1, "g".repeat(2_000), rules, List.of(), List.of()), null));
    }
}
