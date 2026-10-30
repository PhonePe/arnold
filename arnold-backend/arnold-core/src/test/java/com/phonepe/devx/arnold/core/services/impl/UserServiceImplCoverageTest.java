package com.phonepe.devx.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.user.ArnoldUser;
import com.phonepe.devx.arnold.core.model.vcs.ProviderIdentity;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeUserStore;
import com.phonepe.olympus.im.models.user.HumanUserDetails;
import com.phonepe.olympus.im.models.user.SystemUserDetails;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplCoverageTest {
    private final AerospikeUserStore store = mock(AerospikeUserStore.class);
    private final UserServiceImpl service = new UserServiceImpl(store);

    private static HumanUserDetails human(final String name) {
        final var details = new HumanUserDetails();
        details.setUserId("u1");
        details.setName(name);
        details.setEmail("jane.doe@example.com");
        return details;
    }

    private static ArnoldUser user(final String email, final String displayName,
                                   final Map<VersionControlProviderType, ProviderIdentity> identities) {
        return new ArnoldUser("u1", email, "Jane Doe", displayName, identities, Set.of("r"), 1L, 1L);
    }

    @Test
    void testGetOrCreateCurrentUser_shouldCreateUser_whenAbsent() {
        when(store.get("u1")).thenReturn(Optional.empty());

        final var response = service.getOrCreateCurrentUser(human("  Jane Doe "));

        assertEquals("Jane Doe", response.displayName());
        verify(store).create(any(ArnoldUser.class));
    }

    @Test
    void testGetOrCreateCurrentUser_shouldFallBackToUsername_whenNameEmpty() {
        when(store.get("u1")).thenReturn(Optional.empty());

        final var response = service.getOrCreateCurrentUser(human(""));

        assertEquals("Jane Doe", response.displayName());
    }

    @Test
    void testGetOrCreateCurrentUser_shouldReadBackExisting_whenCreateRaces() {
        final var stored = user("jane.doe@example.com", "Jane Doe", Map.of());
        when(store.get("u1")).thenReturn(Optional.empty()).thenReturn(Optional.of(stored));
        doThrow(new IllegalStateException("exists")).when(store).create(any());

        final var response = service.getOrCreateCurrentUser(human("Jane Doe"));

        assertEquals("u1", response.id());
    }

    @Test
    void testGetOrCreateCurrentUser_shouldRethrowCreateFailure_whenUserStillMissing() {
        when(store.get("u1")).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("boom")).when(store).create(any());
        final var details = human("Jane Doe");

        assertThrows(IllegalStateException.class, () -> service.getOrCreateCurrentUser(details));
    }

    @Test
    void testGetOrCreateCurrentUser_shouldRejectSystemUsers() {
        when(store.get("u1")).thenReturn(Optional.empty());
        final var system = new SystemUserDetails();
        system.setUserId("u1");

        final var error = assertThrows(ArnoldError.class, () -> service.getOrCreateCurrentUser(system));

        assertEquals(ArnoldErrorCode.INVALID_USER_FOR_CREATION, error.getErrorCode());
    }

    @Test
    void testGetOrCreateCurrentUser_shouldReturnExisting_whenUpToDate() {
        when(store.get("u1")).thenReturn(Optional.of(user("jane.doe@example.com", "Jane Doe", Map.of())));

        final var response = service.getOrCreateCurrentUser(human("Jane Doe"));

        assertEquals("Jane Doe", response.displayName());
        verify(store, never()).checkAndUpdate(any(), anyLong());
    }

    @Test
    void testGetOrCreateCurrentUser_shouldHealDisplayName_whenStoredNameIsBlankOrShorter() {
        final var blank = user("jane.doe@example.com", " ", Map.of());
        when(store.get("u1")).thenReturn(Optional.of(blank));
        when(store.getWithVersion("u1")).thenReturn(Optional.of(Versioned.of(blank, 4L)));

        final var response = service.getOrCreateCurrentUser(human("Jane Doe"));

        assertEquals("Jane Doe", response.displayName());
        verify(store).checkAndUpdate(any(ArnoldUser.class), org.mockito.ArgumentMatchers.eq(4L));
    }

    @Test
    void testGetOrCreateCurrentUser_shouldHealNullNameAndEmailChange() {
        final var nullName = user("old@example.com", null, Map.of());
        when(store.get("u1")).thenReturn(Optional.of(nullName));
        when(store.getWithVersion("u1")).thenReturn(Optional.of(Versioned.of(nullName, 1L)));

        service.getOrCreateCurrentUser(human("Jane Doe"));

        verify(store).checkAndUpdate(any(ArnoldUser.class), org.mockito.ArgumentMatchers.eq(1L));
    }

    @Test
    void testGetOrCreateCurrentUser_shouldReturnLatestStored_whenHealRaces() {
        final var shortName = user("jane.doe@example.com", "Jane", Map.of());
        final var latest = user("jane.doe@example.com", "Jane Doe", Map.of());
        when(store.get("u1")).thenReturn(Optional.of(shortName)).thenReturn(Optional.of(latest));
        when(store.getWithVersion("u1")).thenReturn(Optional.of(Versioned.of(shortName, 1L)));
        doThrow(new ConcurrentUpdateException("race")).when(store).checkAndUpdate(any(), anyLong());

        final var response = service.getOrCreateCurrentUser(human("Jane Doe"));

        assertEquals("Jane Doe", response.displayName());
    }

    @Test
    void testList_shouldSortByDisplayNameIgnoringCaseWithNullsFirstAndMapIdentities() {
        final var identity = Map.of(VersionControlProviderType.GITLAB, new ProviderIdentity("10", "jd", "http://a"));
        when(store.list()).thenReturn(List.of(user("a", "bob", Map.of()), user("b", null, identity),
                user("c", "Alice", Map.of())));

        final var result = service.list();

        assertNull(result.get(0).displayName());
        assertEquals("http://a", result.get(0).avatarUrl());
        assertEquals("Alice", result.get(1).displayName());
        assertEquals("bob", result.get(2).displayName());
        assertNull(result.get(2).avatarUrl());
    }

    @Test
    void testDeleteUser_shouldDelegateToStore() {
        service.deleteUser("u1");

        verify(store).delete("u1");
    }

    @Test
    void testLinkProviderIdentity_shouldStoreIdentityWithVersion() {
        final var current = user("e", "Jane", Map.of());
        when(store.getWithVersion("u1")).thenReturn(Optional.of(Versioned.of(current, 2L)));

        final var response = service.linkProviderIdentity("u1", VersionControlProviderType.GITLAB,
                new ProviderIdentity("10", "jd", "http://avatar"));

        assertEquals("http://avatar", response.avatarUrl());
        verify(store).checkAndUpdate(any(ArnoldUser.class), org.mockito.ArgumentMatchers.eq(2L));
    }

    @Test
    void testLinkProviderIdentity_shouldRetryThenSucceed_onConcurrentUpdate() {
        final var current = user("e", "Jane", Map.of());
        when(store.getWithVersion("u1")).thenReturn(Optional.of(Versioned.of(current, 2L)));
        doThrow(new ConcurrentUpdateException("race")).doNothing().when(store).checkAndUpdate(any(), anyLong());

        service.linkProviderIdentity("u1", VersionControlProviderType.GITLAB, new ProviderIdentity("1", "j", null));

        verify(store, times(2)).checkAndUpdate(any(), anyLong());
    }

    @Test
    void testLinkProviderIdentity_shouldThrowConflict_whenRetriesExhausted() {
        final var current = user("e", "Jane", Map.of());
        when(store.getWithVersion("u1")).thenReturn(Optional.of(Versioned.of(current, 2L)));
        doThrow(new ConcurrentUpdateException("race")).when(store).checkAndUpdate(any(), anyLong());
        final var identity = new ProviderIdentity("1", "j", null);

        final var error = assertThrows(ArnoldError.class,
                () -> service.linkProviderIdentity("u1", VersionControlProviderType.GITLAB, identity));

        assertEquals(ArnoldErrorCode.CONFLICT_ERROR, error.getErrorCode());
    }

    @Test
    void testLinkProviderIdentity_shouldThrowNotFound_whenUserMissing() {
        when(store.getWithVersion("u1")).thenReturn(Optional.empty());
        final var identity = new ProviderIdentity("1", "j", null);

        final var error = assertThrows(ArnoldError.class,
                () -> service.linkProviderIdentity("u1", VersionControlProviderType.GITLAB, identity));

        assertEquals(ArnoldErrorCode.NOT_FOUND_ERROR, error.getErrorCode());
    }
}
