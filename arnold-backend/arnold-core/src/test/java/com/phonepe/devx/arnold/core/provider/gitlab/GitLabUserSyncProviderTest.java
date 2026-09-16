package com.phonepe.devx.arnold.core.provider.gitlab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.devx.arnold.core.config.GitLabSyncConfiguration;
import com.phonepe.devx.arnold.core.model.sync.UserSyncState;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestSync;
import com.phonepe.devx.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.devx.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlCredential;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GitLabUserSyncProviderTest {
    private final GitLabClient client = mock(GitLabClient.class);
    private final ChangeRequestService changes = mock(ChangeRequestService.class);
    private final VersionControlSyncService sync = mock(VersionControlSyncService.class);
    private final UserMergeRequestService requests = mock(UserMergeRequestService.class);
    private final MergeQueueService queue = mock(MergeQueueService.class);
    private final Map<String, ChangeRequestRecord> records = new HashMap<>();
    private GitLabUserSyncProvider provider;

    @BeforeEach
    void setUp() {
        final var oauth = mock(GitLabOAuthService.class);
        when(oauth.validCredential("user")).thenReturn(new VersionControlCredential("credential", "user",
                VersionControlProviderType.GITLAB, "OAUTH", "token", null, "7", "dev", Set.of(),
                null, "ACTIVE", 0, 0));
        when(changes.records(any())).thenReturn(records);
        when(changes.record(anyString())).thenAnswer(call -> Optional.ofNullable(records.get(call.getArgument(0))));
        doAnswer(call -> {
            final ChangeRequestSync event = call.getArgument(0);
            records.put(event.changeRequest().id(), event.changeRequest());
            return null;
        }).when(sync).synchronize(any(ChangeRequestSync.class));
        provider = new GitLabUserSyncProvider(new GitLabSyncConfiguration("secret"), client, oauth, changes,
                sync, requests, queue, mock(ProviderIdentityService.class), mock(ChangeRequestNotificationPublisher.class));
    }

    @Test
    void testSynchronize_shouldHideExistingOpenAndMergedRequests_whenProjectIsArchived() {
        final var open = record(1, "opened");
        final var merged = record(2, "merged");
        records.put(open.id(), open);
        records.put(merged.id(), merged);
        final var associations = List.of(association(open), association(merged));
        when(requests.associationsForUser("user")).thenReturn(associations);
        when(client.isProjectArchived(10, "token")).thenReturn(true);

        provider.synchronize(UserSyncState.neverSynced(VersionControlProviderType.GITLAB, "user"));

        assertTrue(records.get(open.id()).projectArchived());
        assertTrue(records.get(merged.id()).projectArchived());
        verify(client, times(1)).isProjectArchived(10, "token");
        verify(client, never()).getMergeRequest(anyLong(), anyLong(), anyString());
        verify(requests, times(2)).upsert(eq("user"), argThat(ChangeRequestRecord::projectArchived),
                eq(Set.of(MergeRequestRole.AUTHOR)), anyLong());
        verify(queue).reconcile("user", associations);
    }

    @Test
    void testSynchronize_shouldRestoreArchivedRecords_whenProjectIsUnarchived() {
        final var open = record(1, "opened");
        final var merged = record(2, "merged");
        records.put(open.id(), open.withProjectArchived(true));
        records.put(merged.id(), merged.withProjectArchived(true));
        when(requests.associationsForUser("user")).thenReturn(List.of(association(open), association(merged)));
        when(client.listMergeRequests(7, MergeRequestRole.AUTHOR, "opened", null, "token")).thenReturn(List.of(open));

        provider.synchronize(UserSyncState.neverSynced(VersionControlProviderType.GITLAB, "user"));

        assertFalse(records.get(open.id()).projectArchived());
        assertFalse(records.get(merged.id()).projectArchived());
        verify(client, never()).isProjectArchived(anyLong(), anyString());
    }

    @Test
    void testSynchronize_shouldLeaveQueueUntouched_whenProjectLookupFails() {
        final var open = record(1, "opened");
        records.put(open.id(), open);
        when(requests.associationsForUser("user")).thenReturn(List.of(association(open)));
        when(client.isProjectArchived(10, "token")).thenThrow(new IllegalStateException("GitLab unavailable"));

        assertThrows(IllegalStateException.class,
                () -> provider.synchronize(UserSyncState.neverSynced(VersionControlProviderType.GITLAB, "user")));

        verifyNoInteractions(sync, queue);
        verify(requests, never()).upsert(anyString(), any(), anySet(), anyLong());
    }

    @Test
    void testDeserialize_shouldDefaultToActiveProject_whenStoredRecordPredatesArchiveFlag() throws Exception {
        final var mapper = new ObjectMapper();
        final var archived = record(1, "opened").withProjectArchived(true);
        assertEquals(archived, mapper.readValue(mapper.writeValueAsString(archived), ChangeRequestRecord.class));
        final var legacy = mapper.valueToTree(archived);
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacy).remove("projectArchived");
        assertFalse(mapper.treeToValue(legacy, ChangeRequestRecord.class).projectArchived());
    }

    private ChangeRequestRecord record(final long number, final String state) {
        return new ChangeRequestRecord("gitlab:10:" + number, VersionControlProviderType.GITLAB, "10", "100", number,
                "team/repo", "Title", null, "https://gitlab.example/team/repo/-/merge_requests/" + number,
                "feature", "main", "sha", state, "mergeable", false, 7L, List.of(), List.of(), List.of(), 100, 100);
    }

    private UserChangeRequest association(final ChangeRequestRecord record) {
        return new UserChangeRequest("user:" + record.id(), "user", record.id(), Set.of(MergeRequestRole.AUTHOR),
                true, "opened".equals(record.state()), 0, 0, 100);
    }
}
