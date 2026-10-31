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

import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.ChangeRequestService;
import com.phonepe.arnold.core.storage.ReviewStateStore;
import com.phonepe.arnold.core.storage.UserChangeRequestStore;
import com.phonepe.arnold.core.storage.UserSyncStateStore;
import com.phonepe.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.arnold.models.mergerequest.UserMergeRequestPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserMergeRequestServiceListAllTest {
    private final List<ChangeRequestRecord> stored = List.of(
            record(VersionControlProviderType.GITHUB, "42", 1, "opened", 300, false),
            record(VersionControlProviderType.GITLAB, "10", 2, "opened", 500, false),
            record(VersionControlProviderType.GITHUB, "42", 3, "merged", 400, false),
            record(VersionControlProviderType.GITHUB, "42", 4, "opened", 200, false),
            record(VersionControlProviderType.GITLAB, "11", 5, "opened", 900, true));
    private UserMergeRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        final var store = mock(UserChangeRequestStore.class);
        final var changes = mock(ChangeRequestService.class);
        final var byId = stored.stream().collect(Collectors.toMap(ChangeRequestRecord::id, Function.identity()));
        when(changes.list()).thenReturn(stored);
        when(changes.records(any())).thenAnswer(invocation -> {
            final Collection<String> ids = invocation.getArgument(0);
            return ids.stream().filter(byId::containsKey).collect(Collectors.toMap(id -> id, byId::get));
        });
        when(changes.response(any())).thenAnswer(invocation -> {
            final ChangeRequestRecord record = invocation.getArgument(0);
            final var response = mock(MergeRequestResponse.class);
            when(response.id()).thenReturn(record.id());
            return response;
        });
        when(store.findByUser("carol")).thenReturn(List.of(new UserChangeRequest("carol:github:42:4", "carol",
                "github:42:4", Set.of(MergeRequestRole.REVIEWER), true, true, 0, 0, 0)));
        final var reviews = mock(ReviewStateStore.class);
        when(reviews.get(any())).thenReturn(Map.of());
        service = new UserMergeRequestServiceImpl(store, reviews, mock(UserSyncStateStore.class), changes,
                Clock.systemUTC());
    }

    @Test
    void listsOpenChangeRequestsAcrossProvidersNewestFirstExcludingArchived() {
        final var first = service.listAll("carol", null, "open", 2, null);
        assertEquals(List.of("gitlab:10:2", "github:42:1"), ids(first));
        assertNotNull(first.nextCursor());

        final var second = service.listAll("carol", null, "open", 2, first.nextCursor());
        assertEquals(List.of("github:42:4"), ids(second));
        assertEquals(Set.of("reviewer"), second.items().get(0).roles());
        assertNull(second.nextCursor());
        assertTrue(first.items().get(0).roles().isEmpty());
    }

    @Test
    void filtersByProviderAndState() {
        assertEquals(List.of("github:42:1", "github:42:4"), ids(service.listAll("carol", "github", "open", 50, null)));
        assertEquals(List.of("github:42:3"), ids(service.listAll("carol", "GITHUB", "merged", 50, null)));
        assertEquals(4, service.listAll("carol", "all", "all", 50, null).items().size());
        assertThrows(ArnoldError.class, () -> service.listAll("carol", "bitbucket", "open", 50, null));
        assertThrows(ArnoldError.class, () -> service.listAll("carol", null, "draft", 50, null));
    }

    private static List<String> ids(final UserMergeRequestPageResponse page) {
        return page.items().stream().map(item -> item.mergeRequest().id()).toList();
    }

    private static ChangeRequestRecord record(final VersionControlProviderType provider, final String repositoryId,
                                              final long number, final String state, final long updatedAt,
                                              final boolean archived) {
        return new ChangeRequestRecord(ChangeRequestRecord.key(provider, repositoryId, number), provider,
                repositoryId, Long.toString(number), number, "acme/repo", "t", null, null, "f", "main", "s", state,
                null, false, 1L, List.of(), List.of(), List.of(), updatedAt, updatedAt).withProjectArchived(archived);
    }
}
