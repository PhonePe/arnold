package com.phonepe.arnold.core.provider.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.config.GitHubConfiguration;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.sync.WebhookDelivery;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.ChangeRequestSync;
import com.phonepe.arnold.core.services.ChangeRequestService;
import com.phonepe.arnold.core.services.GitLabWebhookFanoutService;
import com.phonepe.arnold.core.services.VersionControlSyncService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeWebhookDeliveryStore;
import com.phonepe.arnold.models.callback.CallbackResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestResponse;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Processes GitHub App webhooks: verifies {@code X-Hub-Signature-256}, deduplicates on {@code X-GitHub-Delivery} and
 * keeps stored pull requests current from {@code pull_request} and {@code pull_request_review} events.
 */
@Slf4j
@Singleton
public class GitHubWebhookService {
    private static final long DELIVERY_LEASE_MS = 5 * 60_000;
    private static final Set<String> PULL_REQUEST_EVENTS = Set.of("pull_request", "pull_request_review");

    private final GitHubConfiguration config;
    private final GitHubClient client;
    private final ObjectMapper mapper;
    private final AerospikeWebhookDeliveryStore deliveries;
    private final ChangeRequestService changeRequests;
    private final VersionControlSyncService syncService;
    private final GitLabWebhookFanoutService fanout;

    @Inject
    public GitHubWebhookService(final ArnoldCoreConfiguration configuration, final ObjectMapper mapper,
                                final AerospikeWebhookDeliveryStore deliveries,
                                final ChangeRequestService changeRequests, final VersionControlSyncService syncService,
                                final GitLabWebhookFanoutService fanout) {
        this(config(configuration), new GitHubClient(config(configuration), mapper), mapper, deliveries,
                changeRequests, syncService, fanout);
    }

    private static GitHubConfiguration config(final ArnoldCoreConfiguration configuration) {
        return configuration.getGithubConfiguration() == null ? new GitHubConfiguration()
                : configuration.getGithubConfiguration();
    }

    GitHubWebhookService(final GitHubConfiguration config, final GitHubClient client, final ObjectMapper mapper,
                         final AerospikeWebhookDeliveryStore deliveries, final ChangeRequestService changeRequests,
                         final VersionControlSyncService syncService, final GitLabWebhookFanoutService fanout) {
        this.config = config;
        this.client = client;
        this.mapper = mapper;
        this.deliveries = deliveries;
        this.changeRequests = changeRequests;
        this.syncService = syncService;
        this.fanout = fanout;
    }

    public CallbackResponse process(final String signature, final String deliveryId, final String event,
                                    final byte[] body) {
        verifySignature(signature, body);
        if (deliveryId == null || deliveryId.isBlank() || event == null || event.isBlank()) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
        if (!PULL_REQUEST_EVENTS.contains(event)) {
            return new CallbackResponse(deliveryId, "ignored");
        }
        final var id = "github:" + deliveryId;
        final var receivedAt = System.currentTimeMillis();
        if (!claim(id, event, receivedAt)) {
            return new CallbackResponse(deliveryId, "duplicate");
        }
        try {
            synchronize(parse(body).path("pull_request"));
            finish(id, event, receivedAt, "processed");
            return new CallbackResponse(deliveryId, "processed");
        } catch (RuntimeException failure) {
            try {
                finish(id, event, receivedAt, "failed");
            } catch (RuntimeException recovery) {
                failure.addSuppressed(recovery);
            }
            throw failure;
        }
    }

    private void synchronize(final JsonNode pull) {
        if (!pull.isObject() || !pull.path("base").path("repo").path("id").isNumber()) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
        final var current = toRecord(client.toResponse(pull));
        final var previous = changeRequests.record(current.id()).orElse(null);
        final var action = "merged".equals(current.state()) ? ChangeRequestSync.Action.MERGED
                : "closed".equals(current.state()) ? ChangeRequestSync.Action.CLOSED
                : previous != null && "closed".equals(previous.state()) ? ChangeRequestSync.Action.REOPENED
                : ChangeRequestSync.Action.UPSERT;
        if (syncService.synchronize(new ChangeRequestSync(current, action, null)) && fanout != null) {
            fanout.synchronize(previous, current);
        }
    }

    static ChangeRequestRecord toRecord(final MergeRequestResponse pr) {
        return new ChangeRequestRecord(pr.id(), com.phonepe.arnold.core.model.vcs.VersionControlProviderType.GITHUB,
                pr.repositoryId(), pr.providerId(), pr.number(), pr.repositoryPath(), pr.title(), pr.description(),
                pr.webUrl(), pr.sourceBranch(), pr.targetBranch(), pr.headSha(), pr.state(), pr.mergeStatus(),
                pr.draft(), pr.authorId(), pr.authorName(), pr.assigneeIds(), pr.assigneeNames(), pr.reviewerIds(),
                pr.reviewerNames(), List.of(), pr.labels(), pr.createdAt(), pr.mergedAt(), pr.closedAt(),
                pr.hasConflicts(), pr.blockingDiscussionsResolved(), pr.providerUpdatedAt(), pr.lastSyncedAt(), false);
    }

    private void verifySignature(final String signature, final byte[] body) {
        final var secret = config.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw ArnoldError.raise(ArnoldErrorCode.OPERATION_NOT_ALLOWED)
                    .message("GitHub webhook secret is not configured").build();
        }
        final byte[] expected;
        try {
            final var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            expected = ("sha256=" + HexFormat.of().formatHex(mac.doFinal(body))).getBytes(StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
        if (signature == null || !MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8))) {
            throw ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR).message("Invalid GitHub webhook signature").build();
        }
    }

    private JsonNode parse(final byte[] body) {
        try {
            return mapper.readTree(body);
        } catch (IOException e) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
    }

    private boolean claim(final String id, final String event, final long now) {
        final var current = deliveries.getWithVersion(id);
        final var processing = new WebhookDelivery(id, event, "processing", now, 0);
        if (current.isEmpty()) {
            deliveries.create(processing);
            return true;
        }
        final var delivery = current.get().value();
        if ("processed".equals(delivery.status())) {
            return false;
        }
        if ("processing".equals(delivery.status()) && now - delivery.receivedAt() < DELIVERY_LEASE_MS) {
            throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        }
        deliveries.checkAndUpdate(processing, current.get().version());
        return true;
    }

    private void finish(final String id, final String event, final long receivedAt, final String status) {
        final var current = deliveries.getWithVersion(id).orElseThrow();
        deliveries.checkAndUpdate(new WebhookDelivery(id, event, status, receivedAt, System.currentTimeMillis()),
                current.version());
    }
}
