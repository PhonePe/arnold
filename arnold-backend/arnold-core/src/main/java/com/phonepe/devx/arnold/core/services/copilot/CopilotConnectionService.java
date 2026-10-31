package com.phonepe.devx.arnold.core.services.copilot;
import com.phonepe.devx.arnold.models.integration.CopilotExecutionTokenResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.livetheoogway.crudstore.core.ConcurrentStore;
import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.livetheoogway.crudstore.core.Versioned;
import com.phonepe.devx.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.devx.arnold.core.config.CopilotConfiguration;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.copilot.CopilotAccount;
import com.phonepe.devx.arnold.core.model.copilot.CopilotAccount.Attempt;
import com.phonepe.devx.arnold.core.model.copilot.CopilotAccount.Credential;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeCopilotAccountStore;
import com.phonepe.devx.arnold.models.integration.CopilotConnectionResponse;
import com.phonepe.devx.arnold.models.integration.CopilotDeviceResponse;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Clock;
import java.util.UUID;
import java.util.List;
import com.phonepe.devx.arnold.models.analysis.HarnessOption.Model;

@Singleton
public class CopilotConnectionService {
    private static final String CONNECTED = "connected";
    private static final String ACCESS_SUFFIX = ":access";
    private static final String RECONNECT_REQUIRED = "reconnect_required";
    private static final String PENDING = "pending";
    private static final String VERIFICATION_FAILED = "verification_failed";
    private final CopilotConfiguration config;
    private final ConcurrentStore<CopilotAccount, Long> store;
    private final CopilotGitHubClient github;
    private final CopilotTokenCipher cipher;
    private final Clock clock;

    @Inject
    public CopilotConnectionService(final ArnoldCoreConfiguration config, final AerospikeCopilotAccountStore store,
                                     final CopilotGitHubClient github) {
        this(config.getCopilotConfiguration(), store, github, Clock.systemUTC());
    }

    CopilotConnectionService(final CopilotConfiguration config, final ConcurrentStore<CopilotAccount, Long> store,
                              final CopilotGitHubClient github, final Clock clock) {
        this.config = config;
        this.store = store;
        this.github = github;
        this.clock = clock;
        this.cipher = config.isEnabled() ? new CopilotTokenCipher(config.getEncryptionKey()) : null;
        if (config.isEnabled() && (config.getClientId() == null || config.getClientId().isBlank()
                || config.getCredentialSet() == null || config.getCredentialSet().isBlank())) {
            throw new IllegalArgumentException("Copilot clientId and credentialSet are required");
        }
    }

    public List<Model> models(final String user) {
        final var connection = executionConnectionId(user);
        final var credential = executionToken(user, connection);
        try {
            final var result = github.models(credential.accessToken());
            requireConnection(load(user).value().credential(), connection);
            return result;
        } catch (CopilotClientFailure failure) {
            throw ArnoldError.raise(ArnoldErrorCode.CLIENT_FAILURE).build();
        }
    }

    public String executionConnectionId(final String user) {
        enabled();
        final var credential = load(user).value().credential();
        if (credential == null || credential.connectionId() == null || !CONNECTED.equals(credential.status())) {
            throw ArnoldError.raise(ArnoldErrorCode.FORBIDDEN_ERROR).build();
        }
        return credential.connectionId();
    }

    public CopilotExecutionTokenResponse executionToken(
            final String user, final String connectionId) {
        enabled();
        var credential = load(user).value().credential();
        requireConnection(credential, connectionId);
        if (credential.expiresAt() != null && credential.expiresAt() <= clock.millis() + 60_000) {
            if (!verify(user).connected()) throw ArnoldError.raise(ArnoldErrorCode.FORBIDDEN_ERROR).build();
            credential = load(user).value().credential();
            requireConnection(credential, connectionId);
        }
        if (credential.expiresAt() != null && credential.expiresAt() <= clock.millis()) {
            throw ArnoldError.raise(ArnoldErrorCode.FORBIDDEN_ERROR).build();
        }
        return new CopilotExecutionTokenResponse(
                cipher.decrypt(user + ACCESS_SUFFIX, credential.access()), credential.expiresAt());
    }

    private void requireConnection(final Credential credential, final String connectionId) {
        if (credential == null || connectionId == null || !connectionId.equals(credential.connectionId())
                || RECONNECT_REQUIRED.equals(credential.status()) || "copilot_unavailable".equals(credential.status())) {
            throw ArnoldError.raise(ArnoldErrorCode.FORBIDDEN_ERROR).build();
        }
    }

    public CopilotConnectionResponse status(final String user) {
        return response(config.isEnabled() ? store.get(user).map(CopilotAccount::credential).orElse(null) : null);
    }

    public CopilotDeviceResponse initiate(final String user) {
        enabled();
        if (store.get(user).isEmpty()) store.create(new CopilotAccount(user, null, null, null, 0));
        final var current = load(user);
        final var existing = current.value().attempt();
        if (existing != null && PENDING.equals(existing.status()) && existing.expiresAt() > clock.millis()) {
            return device(existing, current.value().credential());
        }
        final var claimed = claim(current);
        try {
            final var data = github.initiate(config.getClientId());
            if (!"https://github.com/login/device".equals(required(data, "verification_uri"))) {
                throw new CopilotClientFailure(502, "upstream_error");
            }
            final var now = clock.millis();
            final var interval = Math.max(5, data.path("interval").asInt(5));
            final var ttl = data.path("expires_in").asLong();
            if (ttl <= 0 || ttl > 3600 || interval > 3600) throw new CopilotClientFailure(502, "upstream_error");
            final var attempt = new Attempt(UUID.randomUUID().toString(),
                    cipher.encrypt(user + ":device", required(data, "device_code")), required(data, "user_code"),
                    required(data, "verification_uri"), now + ttl * 1000, interval, now + interval * 1000L, PENDING);
            save(claimed.value().withAttempt(attempt), claimed.version());
            return device(attempt, claimed.value().credential());
        } catch (CopilotClientFailure e) {
            save(claimed.value().withAttempt(existing), claimed.version());
            throw ArnoldError.raise(ArnoldErrorCode.CLIENT_FAILURE).build();
        }
    }

    public CopilotDeviceResponse poll(final String user, final String attemptId) {
        enabled();
        final var current = load(user);
        final var attempt = requireAttempt(current.value(), attemptId);
        if (!PENDING.equals(attempt.status())) return device(attempt, current.value().credential());
        final var now = clock.millis();
        if (now >= attempt.expiresAt()) return finishAttempt(current, attempt, "expired", attempt.interval());
        if (now < attempt.nextPollAt() || now < current.value().busyUntil()) {
            return device(attempt, current.value().credential());
        }
        final var claimed = claim(current);
        try {
            final var token = github.poll(config.getClientId(), cipher.decrypt(user + ":device", attempt.deviceCode()));
            // Save the newly issued credential before verification, which may fail transiently.
            final var credential = credential(user, token, null);
            final var complete = terminal(attempt, "complete", attempt.interval());
            save(new CopilotAccount(user, credential, complete, null, 0), claimed.version());
            return new CopilotDeviceResponse(attempt.id(), null, null, attempt.expiresAt(), attempt.interval(),
                    "complete", verify(user));
        } catch (CopilotClientFailure e) {
            return switch (e.code()) {
                case "authorization_pending" -> finishAttempt(claimed, attempt, PENDING, attempt.interval());
                case "slow_down" -> finishAttempt(claimed, attempt, PENDING, attempt.interval() + 5);
                case "expired_token", "incorrect_device_code" -> finishAttempt(claimed, attempt, "expired", attempt.interval());
                case "access_denied" -> finishAttempt(claimed, attempt, "denied", attempt.interval());
                default -> {
                    final var pending = finishAttempt(claimed, attempt, PENDING, Math.max(10, attempt.interval()));
                    yield new CopilotDeviceResponse(pending.attemptId(), pending.userCode(), pending.verificationUri(),
                            pending.expiresAt(), pending.interval(), "retrying", pending.connection());
                }
            };
        }
    }

    public CopilotConnectionResponse verify(final String user) {
        enabled();
        final var current = load(user);
        if (current.value().credential() == null) return response(null);
        var claimed = claim(current);
        var credential = claimed.value().credential();
        String verification;
        try {
            if (credential.expiresAt() != null && credential.expiresAt() <= clock.millis() + 60_000) {
                requireRefreshable(credential);
                final var token = github.refresh(config.getClientId(), cipher.decrypt(user + ":refresh", credential.refresh()));
                credential = credential(user, token, credential);
                // Persist rotated refresh tokens before any further network request.
                save(new CopilotAccount(user, credential, claimed.value().attempt(), claimed.value().operationId(),
                        claimed.value().busyUntil()), claimed.version());
                claimed = ownedClaim(user, claimed.value().operationId());
            }
            final var access = cipher.decrypt(user + ACCESS_SUFFIX, credential.access());
            final var identity = github.identity(access);
            credential = new Credential(credential.access(), credential.refresh(), credential.expiresAt(),
                    credential.refreshExpiresAt(), required(identity, "id"), required(identity, "login"),
                    credential.status(), credential.checkedAt(), credential.connectionId());
            verification = verifyCopilot(access);
        } catch (CopilotClientFailure e) {
            verification = failureVerification(e);
        }
        final var connectionId = credential.connectionId() == null ? UUID.randomUUID().toString() : credential.connectionId();
        final var updated = new Credential(credential.access(), credential.refresh(), credential.expiresAt(),
                credential.refreshExpiresAt(), credential.githubUserId(), credential.username(), verification, clock.millis(), connectionId);
        save(claimed.value().withCredential(updated), claimed.version());
        return response(updated);
    }

    private void requireRefreshable(final Credential credential) {
        if (credential.refresh() == null || (credential.refreshExpiresAt() != null
                && credential.refreshExpiresAt() <= clock.millis())) {
            throw new CopilotClientFailure(401, "invalid_grant");
        }
    }

    private static String failureVerification(final CopilotClientFailure e) {
        final boolean reconnect = e.status() == 401 || "bad_refresh_token".equals(e.code())
                || "invalid_grant".equals(e.code());
        return reconnect ? RECONNECT_REQUIRED : VERIFICATION_FAILED;
    }

    private String verifyCopilot(final String access) {
        try {
            github.verifyCopilot(access);
            return CONNECTED;
        } catch (CopilotClientFailure e) {
            if (e.status() == 403) {
                return "copilot_unavailable";
            }
            return e.status() == 401 ? RECONNECT_REQUIRED : VERIFICATION_FAILED;
        }
    }

    public CopilotConnectionResponse cancel(final String user, final String attemptId) {
        enabled();
        final var current = load(user);
        requireAttempt(current.value(), attemptId);
        save(current.value().withAttempt(null), current.version());
        return response(current.value().credential());
    }

    public CopilotConnectionResponse disconnect(final String user) {
        enabled();
        final var current = store.getWithVersion(user);
        // Retain an empty record so an in-flight CAS cannot resurrect deleted credentials.
        current.ifPresent(value -> save(new CopilotAccount(user, null, null, null, 0), value.version()));
        return response(null);
    }

    private Credential credential(final String user, final JsonNode token, final Credential previous) {
        final var refresh = token.path("refresh_token").asText(null);
        final boolean reuseRefresh = refresh == null && previous != null;
        final var access = cipher.encrypt(user + ACCESS_SUFFIX, required(token, "access_token"));
        final var refreshToken = reuseRefresh ? previous.refresh() : cipher.encrypt(user + ":refresh", refresh);
        final var expiresAt = expiry(token, "expires_in");
        final var refreshExpiresAt = reuseRefresh ? previous.refreshExpiresAt()
                : expiry(token, "refresh_token_expires_in");
        final var connectionId = previous == null ? UUID.randomUUID().toString() : previous.connectionId();
        return new Credential(access, refreshToken, expiresAt, refreshExpiresAt,
                previous == null ? null : previous.githubUserId(), previous == null ? null : previous.username(),
                VERIFICATION_FAILED, clock.millis(), connectionId);
    }

    private Long expiry(final JsonNode data, final String field) {
        if (!data.has(field)) return null;
        final var seconds = data.path(field).asLong();
        if (seconds <= 0 || seconds > 315_360_000L) throw new CopilotClientFailure(502, "upstream_error");
        return clock.millis() + seconds * 1000;
    }

    private String required(final JsonNode data, final String field) {
        final var value = data.path(field).asText("");
        if (value.isBlank()) throw new CopilotClientFailure(502, "upstream_error");
        return value;
    }

    private CopilotDeviceResponse finishAttempt(final Versioned<CopilotAccount, Long> current,
                                                final Attempt attempt, final String status, final int interval) {
        final var updated = terminal(attempt, status, interval);
        save(current.value().withAttempt(updated), current.version());
        return device(updated, current.value().credential());
    }

    private Attempt terminal(final Attempt attempt, final String status, final int interval) {
        final var pending = PENDING.equals(status);
        return new Attempt(attempt.id(), pending ? attempt.deviceCode() : null, pending ? attempt.userCode() : null,
                pending ? attempt.verificationUri() : null, attempt.expiresAt(), interval,
                clock.millis() + interval * 1000L, status);
    }

    private Attempt requireAttempt(final CopilotAccount account, final String id) {
        if (account.attempt() == null || !account.attempt().id().equals(id)) {
            throw ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build();
        }
        return account.attempt();
    }

    private CopilotDeviceResponse device(final Attempt attempt, final Credential credential) {
        return new CopilotDeviceResponse(attempt.id(), attempt.userCode(), attempt.verificationUri(), attempt.expiresAt(),
                attempt.interval(), attempt.status(), response(credential));
    }

    private CopilotConnectionResponse response(final Credential credential) {
        final var status = statusOf(credential);
        return new CopilotConnectionResponse(config.isEnabled(), CONNECTED.equals(status),
                credential == null ? null : credential.username(), status,
                credential == null ? null : credential.checkedAt(), credential == null ? null : credential.expiresAt());
    }

    private String statusOf(final Credential credential) {
        if (!config.isEnabled()) {
            return "disabled";
        }
        if (credential == null) {
            return "not_connected";
        }
        final boolean expiredWithoutRefresh = credential.expiresAt() != null
                && credential.expiresAt() <= clock.millis() && credential.refresh() == null;
        return expiredWithoutRefresh ? RECONNECT_REQUIRED : credential.status();
    }

    private Versioned<CopilotAccount, Long> claim(final Versioned<CopilotAccount, Long> current) {
        if (current.value().busyUntil() > clock.millis()) throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        final var operation = UUID.randomUUID().toString();
        save(new CopilotAccount(current.value().id(), current.value().credential(), current.value().attempt(), operation,
                clock.millis() + 90_000), current.version());
        return ownedClaim(current.value().id(), operation);
    }

    private Versioned<CopilotAccount, Long> ownedClaim(final String user, final String operation) {
        final var current = load(user);
        if (!operation.equals(current.value().operationId())) throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        return current;
    }

    private Versioned<CopilotAccount, Long> load(final String user) {
        return store.getWithVersion(user).orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
    }

    private void save(final CopilotAccount value, final Long version) {
        try {
            store.checkAndUpdate(value, version);
        } catch (ConcurrentUpdateException e) {
            throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        }
    }

    private void enabled() {
        if (!config.isEnabled()) throw ArnoldError.raise(ArnoldErrorCode.OPERATION_NOT_ALLOWED).build();
    }
}
