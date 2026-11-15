/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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

import com.phonepe.arnold.core.model.review.*;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.ChangeRequestSync;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.ChangeRequestService;
import com.phonepe.arnold.core.statemachine.StateMachine;
import com.phonepe.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeProjectionEventStore;
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
                "acme/test", "Feature", "Description", "url", "feature", "main", "sha", "closed", "not_open",
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
