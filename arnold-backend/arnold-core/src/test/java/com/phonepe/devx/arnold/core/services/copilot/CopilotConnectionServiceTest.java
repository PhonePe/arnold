package com.phonepe.devx.arnold.core.services.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.devx.arnold.core.config.CopilotConfiguration;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.model.copilot.CopilotAccount;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeCopilotAccountStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.Base64;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CopilotConnectionServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CopilotGitHubClient github = mock(CopilotGitHubClient.class);
    private final AerospikeCopilotAccountStore store = mock(AerospikeCopilotAccountStore.class);
    private final Clock clock = mock(Clock.class);
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final HashMap<String, Versioned<CopilotAccount, Long>> records = new HashMap<>();
    private CopilotConnectionService service;
    private CopilotTokenCipher cipher;

    @BeforeEach
    void setUp() throws Exception {
        final var config = new CopilotConfiguration();
        config.setClientId("test-client-id");
        config.setEnabled(true);
        config.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        cipher = new CopilotTokenCipher(config.getEncryptionKey());
        when(clock.millis()).thenAnswer(call -> now.get());
        when(store.get(anyString())).thenAnswer(call -> Optional.ofNullable(records.get(call.getArgument(0))).map(Versioned::value));
        when(store.getWithVersion(anyString())).thenAnswer(call -> Optional.ofNullable(records.get(call.getArgument(0))));
        doAnswer(call -> {
            final CopilotAccount account = call.getArgument(0);
            if (records.putIfAbsent(account.id(), Versioned.of(account, 1L)) != null) throw new IllegalStateException("exists");
            return null;
        }).when(store).create(any());
        doAnswer(call -> {
            final CopilotAccount account = call.getArgument(0);
            final Long version = call.getArgument(1);
            final var previous = records.get(account.id());
            if (previous == null || !previous.version().equals(version)) throw new ConcurrentUpdateException("stale");
            records.put(account.id(), Versioned.of(account, version + 1));
            return null;
        }).when(store).checkAndUpdate(any(), anyLong());
        when(github.initiate(anyString())).thenReturn(mapper.readTree("""
                {"device_code":"device-secret","user_code":"ABCD-EFGH","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}
                """));
        when(github.poll(anyString(), eq("device-secret"))).thenReturn(mapper.readTree("""
                {"access_token":"oauth-secret","refresh_token":"refresh-secret","expires_in":3600}
                """));
        when(github.identity(anyString())).thenReturn(mapper.readTree("{\"id\":42,\"login\":\"octocat\"}"));
        service = new CopilotConnectionService(config, store, github, clock);
    }

    @Test
    void executionTokensAreBoundToTheConnection() {
        final var attempt = service.initiate("alice");
        now.addAndGet(5000);
        service.poll("alice", attempt.attemptId());
        final var connection = service.executionConnectionId("alice");
        assertEquals("oauth-secret", service.executionToken("alice", connection).accessToken());
        assertThrows(ArnoldError.class, () -> service.executionToken("bob", connection));
        service.disconnect("alice");
        assertThrows(ArnoldError.class, () -> service.executionToken("alice", connection));
        final var replacement = service.initiate("alice");
        now.addAndGet(5000);
        service.poll("alice", replacement.attemptId());
        assertNotEquals(connection, service.executionConnectionId("alice"));
        assertThrows(ArnoldError.class, () -> service.executionToken("alice", connection));
    }

    @Test
    void testConnect_shouldEncryptSecretsAndVerifyCopilot_whenApproved() throws Exception {
        final var attempt = service.initiate("alice");
        assertFalse(mapper.writeValueAsString(attempt).contains("device-secret"));
        assertFalse(mapper.writeValueAsString(records.get("alice")).contains("device-secret"));
        now.addAndGet(5000);
        final var result = service.poll("alice", attempt.attemptId());
        assertEquals("complete", result.status());
        assertTrue(result.connection().connected());
        assertEquals("octocat", result.connection().username());
        assertNull(records.get("alice").value().attempt().deviceCode());
        assertFalse(mapper.writeValueAsString(records).contains("oauth-secret"));
        assertFalse(mapper.writeValueAsString(result).contains("refresh-secret"));
        assertEquals("oauth-secret", cipher.decrypt("alice:access", records.get("alice").value().credential().access()));
        verify(github).verifyCopilot("oauth-secret");
    }

    @Test
    void testPoll_shouldThrottleAndSlowDown_whenRequestedTooOften() {
        final var attempt = service.initiate("alice");
        service.poll("alice", attempt.attemptId());
        verify(github, never()).poll(anyString(), anyString());
        now.addAndGet(5000);
        when(github.poll(anyString(), anyString())).thenThrow(new CopilotClientFailure(400, "slow_down"));
        assertEquals(10, service.poll("alice", attempt.attemptId()).interval());
        now.addAndGet(5000);
        service.poll("alice", attempt.attemptId());
        verify(github, times(1)).poll(anyString(), anyString());
    }

    @Test
    void testPoll_shouldRemainPending_whenApprovalOrNetworkIsPending() {
        final var attempt = service.initiate("alice");
        now.addAndGet(5000);
        when(github.poll(anyString(), anyString())).thenThrow(new CopilotClientFailure(400, "authorization_pending"));
        assertEquals("pending", service.poll("alice", attempt.attemptId()).status());
        now.addAndGet(5000);
        doThrow(new CopilotClientFailure(502, "upstream_error")).when(github).poll(anyString(), anyString());
        assertEquals("retrying", service.poll("alice", attempt.attemptId()).status());
    }

    @Test
    void testPoll_shouldRejectCrossUserAttempts_whenIdIsKnown() {
        final var attempt = service.initiate("alice");
        service.initiate("bob");
        assertThrows(ArnoldError.class, () -> service.poll("bob", attempt.attemptId()));
        assertThrows(ArnoldError.class, () -> service.cancel("bob", attempt.attemptId()));
        verify(github, never()).poll(anyString(), anyString());
    }

    @Test
    void testPoll_shouldClearDeviceSecret_whenDeniedOrExpired() {
        final var attempt = service.initiate("alice");
        now.addAndGet(5000);
        when(github.poll(anyString(), anyString())).thenThrow(new CopilotClientFailure(400, "access_denied"));
        assertEquals("denied", service.poll("alice", attempt.attemptId()).status());
        assertNull(records.get("alice").value().attempt().deviceCode());
        final var replacement = service.initiate("alice");
        now.addAndGet(900_000);
        assertEquals("expired", service.poll("alice", replacement.attemptId()).status());
        assertNull(records.get("alice").value().attempt().deviceCode());
    }

    @Test
    void testPoll_shouldNotRestoreCredential_whenDisconnectedDuringExchange() throws Exception {
        final var attempt = service.initiate("alice");
        now.addAndGet(5000);
        when(github.poll(anyString(), anyString())).thenAnswer(call -> {
            service.disconnect("alice");
            return mapper.readTree("{\"access_token\":\"late-token\"}");
        });
        assertThrows(ArnoldError.class, () -> service.poll("alice", attempt.attemptId()));
        assertNull(records.get("alice").value().credential());
        assertNull(records.get("alice").value().attempt());
    }

    @Test
    void testPoll_shouldNotRestoreCredential_whenCancelledDuringExchange() throws Exception {
        final var attempt = service.initiate("alice");
        now.addAndGet(5000);
        when(github.poll(anyString(), anyString())).thenAnswer(call -> {
            service.cancel("alice", attempt.attemptId());
            return mapper.readTree("{\"access_token\":\"late-token\"}");
        });
        assertThrows(ArnoldError.class, () -> service.poll("alice", attempt.attemptId()));
        assertNull(records.get("alice").value().credential());
    }

    @Test
    void testPoll_shouldNotExchangeTwice_whenAnotherPollIsInFlight() throws Exception {
        final var attempt = service.initiate("alice");
        now.addAndGet(5000);
        when(github.poll(anyString(), anyString())).thenAnswer(call -> {
            assertEquals("pending", service.poll("alice", attempt.attemptId()).status());
            return mapper.readTree("{\"access_token\":\"oauth-secret\"}");
        });
        assertEquals("complete", service.poll("alice", attempt.attemptId()).status());
        verify(github, times(1)).poll(anyString(), anyString());
    }

    @Test
    void testVerify_shouldDistinguishUnavailableAndTransientFailure_whenCopilotRejectsAccess() {
        doThrow(new CopilotClientFailure(403, "upstream_error")).when(github).verifyCopilot(anyString());
        connect();
        assertEquals("copilot_unavailable", service.status("alice").status());
        doThrow(new CopilotClientFailure(502, "upstream_error")).when(github).verifyCopilot(anyString());
        assertEquals("verification_failed", service.verify("alice").status());
        assertNotNull(records.get("alice").value().credential());
    }

    @Test
    void testReconnect_shouldPreserveCredential_whenNewAttemptIsCancelled() {
        connect();
        final var original = records.get("alice").value().credential();
        final var attempt = service.initiate("alice");
        service.cancel("alice", attempt.attemptId());
        assertEquals(original, records.get("alice").value().credential());
        assertTrue(service.status("alice").connected());
    }

    @Test
    void testVerify_shouldPersistRotatedToken_whenLaterVerificationFails() throws Exception {
        connect();
        now.addAndGet(3_600_000);
        when(github.refresh(anyString(), eq("refresh-secret"))).thenReturn(mapper.readTree("""
                {"access_token":"rotated-access","refresh_token":"rotated-refresh","expires_in":3600}
                """));
        when(github.identity("rotated-access")).thenThrow(new CopilotClientFailure(502, "upstream_error"));
        assertEquals("verification_failed", service.verify("alice").status());
        assertEquals("rotated-refresh", cipher.decrypt("alice:refresh", records.get("alice").value().credential().refresh()));
    }

    @Test
    void testVerify_shouldRequireReconnect_whenRefreshIsRejected() {
        connect();
        now.addAndGet(3_600_000);
        when(github.refresh(anyString(), anyString())).thenThrow(new CopilotClientFailure(400, "bad_refresh_token"));
        assertEquals("reconnect_required", service.verify("alice").status());
    }

    @Test
    void testVerify_shouldNotRestoreCredential_whenDisconnectedDuringVerification() {
        connect();
        doAnswer(call -> { service.disconnect("alice"); return null; }).when(github).verifyCopilot(anyString());
        assertThrows(ArnoldError.class, () -> service.verify("alice"));
        assertNull(records.get("alice").value().credential());
    }

    @Test
    void testStatus_shouldAvoidStorage_whenDisabled() {
        final var disabled = new CopilotConnectionService(new CopilotConfiguration(), store, github, clock);
        assertEquals("disabled", disabled.status("alice").status());
        assertThrows(ArnoldError.class, () -> disabled.initiate("alice"));
        verifyNoInteractions(store, github);
    }

    private void connect() {
        final var attempt = service.initiate("alice");
        now.addAndGet(5000);
        service.poll("alice", attempt.attemptId());
    }
}
