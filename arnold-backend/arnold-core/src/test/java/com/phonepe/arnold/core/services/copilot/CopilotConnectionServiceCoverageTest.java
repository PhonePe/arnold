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

package com.phonepe.arnold.core.services.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.arnold.core.config.CopilotConfiguration;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.model.copilot.CopilotAccount;
import com.phonepe.arnold.core.storage.aerospike.AerospikeCopilotAccountStore;
import com.phonepe.arnold.models.analysis.HarnessOption.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CopilotConnectionServiceCoverageTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CopilotGitHubClient github = mock(CopilotGitHubClient.class);
    private final AerospikeCopilotAccountStore store = mock(AerospikeCopilotAccountStore.class);
    private final Clock clock = mock(Clock.class);
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final HashMap<String, Versioned<CopilotAccount, Long>> records = new HashMap<>();
    private CopilotConfiguration config;
    private CopilotConnectionService service;

    @BeforeEach
    void setUp() throws Exception {
        config = new CopilotConfiguration();
        config.setClientId("cid");
        config.setEnabled(true);
        config.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        when(clock.millis()).thenAnswer(call -> now.get());
        when(store.get(anyString())).thenAnswer(c -> Optional.ofNullable(records.get(c.<String>getArgument(0))).map(Versioned::value));
        when(store.getWithVersion(anyString())).thenAnswer(c -> Optional.ofNullable(records.get(c.<String>getArgument(0))));
        doAnswer(call -> {
            final CopilotAccount a = call.getArgument(0);
            records.put(a.id(), Versioned.of(a, 1L));
            return null;
        }).when(store).create(any());
        doAnswer(call -> {
            final CopilotAccount a = call.getArgument(0);
            final Long v = call.getArgument(1);
            final var prev = records.get(a.id());
            if (prev == null || !prev.version().equals(v)) throw new ConcurrentUpdateException("stale");
            records.put(a.id(), Versioned.of(a, v + 1));
            return null;
        }).when(store).checkAndUpdate(any(), anyLong());
        when(github.initiate(anyString())).thenReturn(device(900, 5, "https://github.com/login/device"));
        when(github.poll(anyString(), anyString())).thenReturn(mapper.readTree(
                "{\"access_token\":\"acc\",\"refresh_token\":\"ref\",\"expires_in\":3600,\"refresh_token_expires_in\":86400}"));
        when(github.identity(anyString())).thenReturn(mapper.readTree("{\"id\":1,\"login\":\"octo\"}"));
        service = new CopilotConnectionService(config, store, github, clock);
    }

    private com.fasterxml.jackson.databind.JsonNode device(final long ttl, final int interval, final String uri) throws Exception {
        return mapper.readTree("{\"device_code\":\"dc\",\"user_code\":\"UC\",\"verification_uri\":\"" + uri
                + "\",\"expires_in\":" + ttl + ",\"interval\":" + interval + "}");
    }

    private void connect() {
        final var attempt = service.initiate("alice");
        now.addAndGet(5000);
        service.poll("alice", attempt.attemptId());
    }

    @Test
    void constructorRequiresClientIdAndCredentialSetWhenEnabled() {
        config.setClientId(" ");
        assertThrows(IllegalArgumentException.class, () -> new CopilotConnectionService(config, store, github, clock));
        config.setClientId("cid");
        config.setCredentialSet(null);
        assertThrows(IllegalArgumentException.class, () -> new CopilotConnectionService(config, store, github, clock));
        config.setCredentialSet("");
        assertThrows(IllegalArgumentException.class, () -> new CopilotConnectionService(config, store, github, clock));
    }

    @Test
    void modelsReturnsGithubModelsForConnectedUser() {
        connect();
        final var expected = List.of(new Model("m", "M", true));
        when(github.models("acc")).thenReturn(expected);
        assertEquals(expected, service.models("alice"));
    }

    @Test
    void modelsMapsClientFailureAndRejectsWhenNotConnected() {
        assertThrows(ArnoldError.class, () -> service.models("ghost"));
        connect();
        when(github.models(anyString())).thenThrow(new CopilotClientFailure(502, "upstream_error"));
        assertThrows(ArnoldError.class, () -> service.models("alice"));
    }

    @Test
    void executionConnectionIdRejectsMissingOrDisconnectedCredential() {
        service.initiate("alice");
        assertThrows(ArnoldError.class, () -> service.executionConnectionId("alice"));
        doThrow(new CopilotClientFailure(403, "upstream_error")).when(github).verifyCopilot(anyString());
        service.verify("alice");
        connect();
        assertThrows(ArnoldError.class, () -> service.executionConnectionId("alice"));
    }

    @Test
    void executionTokenRefreshesNearExpiryAndFailsWhenExpiredWithoutVerification() throws Exception {
        connect();
        final var id = service.executionConnectionId("alice");
        now.addAndGet(3_600_000 - 30_000);
        when(github.refresh(anyString(), eq("ref"))).thenReturn(mapper.readTree(
                "{\"access_token\":\"acc2\",\"expires_in\":3600}"));
        assertEquals("acc2", service.executionToken("alice", id).accessToken());

        now.addAndGet(3_600_000 - 30_000);
        when(github.refresh(anyString(), anyString())).thenThrow(new CopilotClientFailure(400, "bad_refresh_token"));
        assertThrows(ArnoldError.class, () -> service.executionToken("alice", id));
    }

    @Test
    void executionTokenRejectsUnknownConnectionAndDisabledService() {
        connect();
        assertThrows(ArnoldError.class, () -> service.executionToken("alice", "other"));
        assertThrows(ArnoldError.class, () -> service.executionToken("alice", null));
        config.setEnabled(false);
        final var disabled = new CopilotConnectionService(config, store, github, clock);
        assertThrows(ArnoldError.class, () -> disabled.executionToken("alice", "x"));
        assertThrows(ArnoldError.class, () -> disabled.executionConnectionId("alice"));
        assertThrows(ArnoldError.class, () -> disabled.models("alice"));
        assertThrows(ArnoldError.class, () -> disabled.poll("alice", "x"));
        assertThrows(ArnoldError.class, () -> disabled.verify("alice"));
        assertThrows(ArnoldError.class, () -> disabled.cancel("alice", "x"));
        assertThrows(ArnoldError.class, () -> disabled.disconnect("alice"));
    }

    @Test
    void executionTokenRejectsReconnectRequiredCredential() {
        connect();
        final var id = service.executionConnectionId("alice");
        when(github.identity(anyString())).thenThrow(new CopilotClientFailure(401, "upstream_error"));
        assertEquals("reconnect_required", service.verify("alice").status());
        assertThrows(ArnoldError.class, () -> service.executionToken("alice", id));
    }

    @Test
    void initiateReturnsExistingPendingAttemptWithoutCallingGithub() {
        final var first = service.initiate("alice");
        final var second = service.initiate("alice");
        assertEquals(first.attemptId(), second.attemptId());
        verify(github, times(1)).initiate(anyString());
    }

    @Test
    void initiateRejectsInvalidUpstreamPayloadsAndKeepsPreviousAttempt() throws Exception {
        for (final var payload : List.of(
                device(900, 5, "https://evil.example/login"),
                device(0, 5, "https://github.com/login/device"),
                device(7200, 5, "https://github.com/login/device"),
                device(900, 7200, "https://github.com/login/device"),
                mapper.readTree("{\"verification_uri\":\"https://github.com/login/device\",\"expires_in\":900}"))) {
            when(github.initiate(anyString())).thenReturn(payload);
            assertThrows(ArnoldError.class, () -> service.initiate("alice"));
            assertNull(records.get("alice").value().attempt());
        }
        when(github.initiate(anyString())).thenThrow(new CopilotClientFailure(502, "upstream_error"));
        assertThrows(ArnoldError.class, () -> service.initiate("alice"));
    }

    @Test
    void initiateConflictsWhileAnotherOperationIsInFlight() {
        service.initiate("alice");
        final var current = records.get("alice");
        final var acct = current.value();
        records.put("alice", Versioned.of(new CopilotAccount("alice", null, null, "op", now.get() + 10_000), current.version()));
        assertThrows(ArnoldError.class, () -> service.initiate("alice"));
        assertNotNull(acct);
    }

    @Test
    void pollHandlesTerminalAttemptsEarlyPollAndErrorCodes() {
        final var attempt = service.initiate("alice");
        assertThrows(ArnoldError.class, () -> service.poll("alice", "wrong"));
        assertEquals("pending", service.poll("alice", attempt.attemptId()).status());

        now.addAndGet(5000);
        doThrow(new CopilotClientFailure(400, "expired_token")).when(github).poll(anyString(), anyString());
        assertEquals("expired", service.poll("alice", attempt.attemptId()).status());
        verify(github, times(1)).poll(anyString(), anyString());
        assertEquals("expired", service.poll("alice", attempt.attemptId()).status());
        verify(github, times(1)).poll(anyString(), anyString());

        final var next = service.initiate("alice");
        now.addAndGet(10_000);
        doThrow(new CopilotClientFailure(400, "incorrect_device_code")).when(github).poll(anyString(), anyString());
        assertEquals("expired", service.poll("alice", next.attemptId()).status());
    }

    @Test
    void pollRespectsBusyUntil() {
        final var attempt = service.initiate("alice");
        now.addAndGet(5000);
        final var current = records.get("alice");
        records.put("alice", Versioned.of(new CopilotAccount("alice", null, current.value().attempt(), null,
                now.get() + 5000), current.version()));
        service.poll("alice", attempt.attemptId());
        verify(github, never()).poll(anyString(), anyString());
    }

    @Test
    void pollRejectsInvalidTokenLifetimes() throws Exception {
        final var attempt = service.initiate("alice");
        now.addAndGet(5000);
        when(github.poll(anyString(), anyString())).thenReturn(mapper.readTree(
                "{\"access_token\":\"acc\",\"expires_in\":-1}"));
        assertEquals("retrying", service.poll("alice", attempt.attemptId()).status());
        now.addAndGet(10_000);
        when(github.poll(anyString(), anyString())).thenReturn(mapper.readTree(
                "{\"access_token\":\"acc\"}"));
        final var done = service.poll("alice", attempt.attemptId());
        assertEquals("complete", done.status());
        assertNull(records.get("alice").value().credential().expiresAt());
    }

    @Test
    void verifyWithoutCredentialReportsNotConnected() {
        service.initiate("alice");
        assertEquals("not_connected", service.verify("alice").status());
        assertThrows(ArnoldError.class, () -> service.verify("ghost"));
    }

    @Test
    void verifyRefreshesExpiringTokenKeepingPreviousRefreshWhenNotRotated() throws Exception {
        connect();
        now.addAndGet(3_600_000);
        when(github.refresh(anyString(), eq("ref"))).thenReturn(mapper.readTree(
                "{\"access_token\":\"acc2\",\"expires_in\":3600}"));
        assertTrue(service.verify("alice").connected());
        verify(github).identity("acc2");
    }

    @Test
    void verifyRequiresReconnectWhenRefreshTokenMissingOrExpired() {
        connect();
        final var rec = records.get("alice");
        final var cred = rec.value().credential();
        final var expired = new CopilotAccount.Credential(cred.access(), cred.refresh(), now.get() - 1, now.get() - 1,
                cred.githubUserId(), cred.username(), cred.status(), cred.checkedAt(), cred.connectionId());
        records.put("alice", Versioned.of(rec.value().withCredential(expired), rec.version()));
        assertEquals("reconnect_required", service.verify("alice").status());
        assertEquals("reconnect_required", service.status("alice").status());
    }

    @Test
    void statusReportsReconnectWhenExpiredWithoutRefreshAndNotConnectedForUnknownUser() {
        assertEquals("not_connected", service.status("nobody").status());
        connect();
        final var rec = records.get("alice");
        final var cred = rec.value().credential();
        final var noRefresh = new CopilotAccount.Credential(cred.access(), null, now.get() - 1, null,
                cred.githubUserId(), cred.username(), cred.status(), cred.checkedAt(), cred.connectionId());
        records.put("alice", Versioned.of(rec.value().withCredential(noRefresh), rec.version()));
        assertEquals("reconnect_required", service.status("alice").status());
    }

    @Test
    void verifyMapsCopilotUnauthorizedToReconnect() {
        connect();
        doThrow(new CopilotClientFailure(401, "upstream_error")).when(github).verifyCopilot(anyString());
        assertEquals("reconnect_required", service.verify("alice").status());
    }

    @Test
    void cancelAndDisconnectClearState() {
        final var attempt = service.initiate("alice");
        assertNull(service.cancel("alice", attempt.attemptId()).username());
        assertNull(records.get("alice").value().attempt());
        assertEquals("not_connected", service.disconnect("alice").status());
        assertEquals("not_connected", service.disconnect("unknown").status());
    }

    @Test
    void concurrentUpdateSurfacesAsConflict() {
        service.initiate("alice");
        doThrow(new ConcurrentUpdateException("stale")).when(store).checkAndUpdate(any(), anyLong());
        assertThrows(ArnoldError.class, () -> service.cancel("alice", records.get("alice").value().attempt() == null
                ? "x" : records.get("alice").value().attempt().id()));
    }
}
