package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.model.review.*;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestSync;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.services.ChangeRequestService;
import com.phonepe.devx.arnold.core.statemachine.StateMachine;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeProjectionEventStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VersionControlSyncServiceImplTest {
    @Test
    @SuppressWarnings("unchecked")
    void testSynchronize_shouldReportAcceptanceAndSkipSideEffects_whenSnapshotIsStale() {
        final var changes = mock(ChangeRequestService.class);
        final var comments = mock(AerospikeCommentStore.class);
        final var projections = mock(AerospikeProjectionEventStore.class);
        final StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> machine = mock(StateMachine.class);
        final var service = new VersionControlSyncServiceImpl(changes, comments, new ProjectionQueue(projections), machine);
        final var record = new ChangeRequestRecord("gitlab:10:12", VersionControlProviderType.GITLAB, "10", "100", 12,
                "devx/test", "Feature", "Description", "url", "feature", "main", "sha", "closed", "not_open",
                false, 1L, List.of(), List.of(), List.of(), 10, 20);
        final var event = new ChangeRequestSync(record, ChangeRequestSync.Action.CLOSED, "author");
        when(changes.synchronize(record)).thenReturn(false, true);

        assertFalse(service.synchronize(event));
        verifyNoInteractions(machine, projections, comments);
        assertTrue(service.synchronize(event));
        verify(machine).tryFire(eq(record.id()), any());
        verify(projections).create(any(), anyList());
    }
}
