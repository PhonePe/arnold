package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.devx.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.devx.arnold.core.services.ChangeRequestService;
import com.phonepe.devx.arnold.core.storage.ReviewStateStore;
import com.phonepe.devx.arnold.core.storage.UserChangeRequestStore;
import com.phonepe.devx.arnold.core.storage.UserSyncStateStore;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserMergeRequestServiceImplTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-16T00:00:00Z"), ZoneOffset.UTC);
    private final List<UserChangeRequest> associations = new ArrayList<>();
    private final Map<String, ChangeRequestRecord> records = new HashMap<>();
    private UserMergeRequestServiceImpl service;
    private UserChangeRequestStore store;

    @BeforeEach
    void setUp() {
        store = mock(UserChangeRequestStore.class);
        final var changes = mock(ChangeRequestService.class);
        when(store.findByUser(any())).thenAnswer(invocation -> associations.stream()
                .filter(item -> item.userId().equals(invocation.getArgument(0))).toList());
        when(changes.records(any())).thenAnswer(invocation -> {
            final java.util.Collection<String> ids = invocation.getArgument(0);
            return ids.stream().filter(records::containsKey).collect(Collectors.toMap(id -> id, records::get));
        });
        when(changes.response(any())).thenAnswer(invocation -> {
            final ChangeRequestRecord record = invocation.getArgument(0);
            final var response = mock(MergeRequestResponse.class);
            final var id = record.id();
            when(response.id()).thenReturn(id);
            return response;
        });
        service = new UserMergeRequestServiceImpl(store, mock(ReviewStateStore.class),
                mock(UserSyncStateStore.class), changes, clock);
    }

    @Test
    void testList_shouldReturnOnlyOpenWork_whenStateIsOmitted() {
        add("open", "user", "opened", 0, MergeRequestRole.AUTHOR);
        add("merged", "user", "merged", clock.millis(), MergeRequestRole.AUTHOR);
        final var page = service.list("user", "all", 50, null);
        assertEquals(List.of("open"), page.items().stream().map(item -> item.mergeRequest().id()).toList());
    }

    @Test
    void testList_shouldPageRecentMergesInMergeOrder_whenStateIsMerged() {
        final var cutoff = clock.millis() - Duration.ofDays(30).toMillis();
        add("boundary", "user", "merged", cutoff, MergeRequestRole.ASSIGNEE);
        add("old", "user", "merged", cutoff - 1, MergeRequestRole.AUTHOR);
        add("newest", "user", "merged", clock.millis(), MergeRequestRole.REVIEWER);
        add("closed", "user", "closed", clock.millis(), MergeRequestRole.AUTHOR);
        add("open", "user", "opened", 0, MergeRequestRole.AUTHOR);
        add("private", "other-user", "merged", clock.millis(), MergeRequestRole.AUTHOR);
        final var first = service.list("user", "all", 1, null, "merged");
        assertEquals("newest", first.items().get(0).mergeRequest().id());
        assertNotNull(first.nextCursor());
        final var second = service.list("user", "all", 1, first.nextCursor(), "merged");
        assertEquals("boundary", second.items().get(0).mergeRequest().id());
        assertNull(second.nextCursor());
        assertEquals(Set.of("assignee"), second.items().get(0).roles());
        assertEquals(1, service.list("user", "reviewer", 50, null, "merged").items().size());
        assertTrue(service.list("unknown-user", "all", 50, null, "merged").items().isEmpty());
        verify(store, never()).findByUser("other-user");
    }

    @Test
    void testListAndSummaries_shouldExcludeArchivedProjectsIncludingGroupMembers() {
        add("archived-open", "user", "opened", 0, MergeRequestRole.AUTHOR);
        add("active-open", "user", "opened", 0, MergeRequestRole.REVIEWER);
        add("archived-merged", "user", "merged", clock.millis(), MergeRequestRole.AUTHOR);
        when(records.get("archived-open").projectArchived()).thenReturn(true);
        when(records.get("archived-merged").projectArchived()).thenReturn(true);

        final var page = service.list("user", "all", 1, null);
        assertEquals(List.of("active-open"), page.items().stream().map(item -> item.mergeRequest().id()).toList());
        assertNull(page.nextCursor());
        assertTrue(service.list("user", "all", 50, null, "merged").items().isEmpty());
        assertEquals(Set.of("active-open"), service.summaries("user", records.keySet()).keySet());
        assertFalse(service.upsert("user", records.get("archived-open"), Set.of(MergeRequestRole.AUTHOR), 1).active());
    }

    @Test
    void testList_shouldRejectInvalidInput_whenStateOrCursorIsInvalid() {
        assertThrows(RuntimeException.class, () -> service.list("user", "all", 50, null, "closed"));
        assertThrows(RuntimeException.class, () -> service.list("user", "all", 50, "LTE", "merged"));
        assertThrows(RuntimeException.class, () -> service.list("user", "all", 50, "!invalid", "merged"));
    }

    private void add(final String id, final String userId, final String state, final long mergedAt,
                     final MergeRequestRole role) {
        final var record = mock(ChangeRequestRecord.class);
        when(record.id()).thenReturn(id);
        when(record.state()).thenReturn(state);
        when(record.mergedAt()).thenReturn(mergedAt);
        when(record.providerUpdatedAt()).thenReturn(-mergedAt);
        records.put(id, record);
        associations.add(new UserChangeRequest(userId + ":" + id, userId, id, Set.of(role), true,
                "opened".equals(state), 0, 0, 0));
    }
}
