package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.config.GitLabSyncConfiguration;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.comment.CommentRecord;
import com.phonepe.devx.arnold.core.model.sync.WebhookDelivery;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestSync;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.services.GitLabCallbackService;
import com.phonepe.devx.arnold.core.services.GitLabWebhookFanoutService;
import com.phonepe.devx.arnold.core.services.UserSyncControlService;
import com.phonepe.devx.arnold.core.services.ChangeRequestService;
import com.phonepe.devx.arnold.core.services.VersionControlSyncService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeWebhookDeliveryStore;
import com.phonepe.devx.arnold.models.callback.CallbackResponse;
import com.phonepe.devx.arnold.models.callback.GitLabWebhookRequest;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Singleton
@Slf4j
public class GitLabCallbackServiceImpl implements GitLabCallbackService {
    private final GitLabSyncConfiguration configuration;
    private final AerospikeWebhookDeliveryStore deliveryStore;
    private final VersionControlSyncService syncService;
    private final ChangeRequestService changeRequestService;
    private final GitLabWebhookFanoutService webhookFanoutService;
    private final UserSyncControlService syncControlService;

    @Inject
    public GitLabCallbackServiceImpl(final @Config GitLabSyncConfiguration configuration,
                                     final AerospikeWebhookDeliveryStore deliveryStore,
                                     final VersionControlSyncService syncService,
                                     final ChangeRequestService changeRequestService,
                                     final GitLabWebhookFanoutService webhookFanoutService,
                                     final UserSyncControlService syncControlService) {
        this.configuration = configuration;
        this.deliveryStore = deliveryStore;
        this.syncService = syncService;
        this.changeRequestService = changeRequestService;
        this.webhookFanoutService = webhookFanoutService;
        this.syncControlService = syncControlService;
    }

    public GitLabCallbackServiceImpl(final GitLabSyncConfiguration configuration,
                                     final AerospikeWebhookDeliveryStore deliveryStore,
                                     final VersionControlSyncService syncService) {
        this(configuration, deliveryStore, syncService, null, null, null);
    }

    @Override
    public CallbackResponse process(final String token, final String deliveryId, final String eventType,
                                    final GitLabWebhookRequest request) {
        verifyToken(token);
        if (deliveryId == null || deliveryId.isBlank()) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
        log.info("GitLab webhook received deliveryId:{} eventType:{}", deliveryId, eventType);
        if (deliveryStore.get(deliveryId).isPresent()) {
            log.info("GitLab webhook duplicate deliveryId:{} eventType:{}", deliveryId, eventType);
            return new CallbackResponse(deliveryId, "duplicate");
        }
        final var receivedAt = System.currentTimeMillis();
        try {
            deliveryStore.create(new WebhookDelivery(deliveryId, eventType, "processing", receivedAt, 0));
        } catch (RuntimeException createRace) {
            if (deliveryStore.get(deliveryId).isPresent()) {
                log.info("GitLab webhook duplicate deliveryId:{} eventType:{}", deliveryId, eventType);
                return new CallbackResponse(deliveryId, "duplicate");
            }
            throw createRace;
        }
        try {
            processEvent(request);
            if (syncControlService != null) {
                syncControlService.callbackReceived();
            }
            deliveryStore.update(new WebhookDelivery(deliveryId, eventType, "processed", receivedAt,
                    System.currentTimeMillis()));
            log.info("GitLab webhook processed deliveryId:{} eventType:{}", deliveryId, eventType);
            return new CallbackResponse(deliveryId, "processed");
        } catch (RuntimeException failure) {
            deliveryStore.delete(deliveryId);
            throw failure;
        }
    }

    private void processEvent(final GitLabWebhookRequest request) {
        final var kind = request.objectKind() == null ? "" : request.objectKind().toLowerCase(Locale.ROOT);
        switch (kind) {
            case "merge_request" -> processMergeRequest(request);
            case "note" -> processNote(request);
            default -> throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
    }

    private void processMergeRequest(final GitLabWebhookRequest request) {
        final var attributes = required(request.attributes());
        final var project = required(request.project());
        final var now = System.currentTimeMillis();
        final var updatedAt = timestamp(attributes.updatedAt(), now);
        final var repositoryId = Long.toString(project.id());
        final var createdAt = timestamp(attributes.createdAt(), updatedAt);
        final var mergedAt = "merged".equalsIgnoreCase(attributes.state()) ? updatedAt : 0;
        final var closedAt = "closed".equalsIgnoreCase(attributes.state()) ? updatedAt : 0;
        final var mergeStatus = attributes.mergeStatus();
        final var mergeRequest = new ChangeRequestRecord(
                ChangeRequestRecord.key(VersionControlProviderType.GITLAB, repositoryId, attributes.iid()),
                VersionControlProviderType.GITLAB, repositoryId, Long.toString(attributes.id()), attributes.iid(),
                project.pathWithNamespace(), attributes.title(), attributes.description(), attributes.url(),
                attributes.sourceBranch(), attributes.targetBranch(),
                attributes.lastCommit() == null ? null : attributes.lastCommit().id(), attributes.state(),
                mergeStatus, isDraft(attributes.title()), attributes.authorId(), authorName(request),
                ids(request.assignees()), names(request.assignees()), ids(request.reviewers()), names(request.reviewers()),
                states(request.reviewers()), request.labels() == null ? List.of() : request.labels().stream()
                        .map(GitLabWebhookRequest.Label::title).toList(),
                createdAt, mergedAt, closedAt, "cannot_be_merged".equalsIgnoreCase(mergeStatus)
                || "conflict".equalsIgnoreCase(mergeStatus),
                !Boolean.FALSE.equals(attributes.blockingDiscussionsResolved()), updatedAt, now, false);
        final var previous = changeRequestService == null
                ? null : changeRequestService.record(mergeRequest.id()).orElse(null);
        final var current = mergeRequest.withProjectArchived(previous != null && previous.projectArchived());
        syncService.synchronize(new ChangeRequestSync(current, action(attributes),
                request.user() == null ? null : request.user().username()));
        if (webhookFanoutService != null) {
            webhookFanoutService.synchronize(previous, current);
        }
    }

    private void processNote(final GitLabWebhookRequest request) {
        final var attributes = required(request.attributes());
        final var project = required(request.project());
        final var mergeRequest = required(request.mergeRequest());
        if (!"MergeRequest".equals(attributes.noteableType())) {
            return;
        }
        final var now = System.currentTimeMillis();
        final var updatedAt = timestamp(attributes.updatedAt(), now);
        final var repositoryId = Long.toString(project.id());
        final var id = CommentRecord.key(VersionControlProviderType.GITLAB, repositoryId,
                mergeRequest.iid(), attributes.id());
        final var comment = new CommentRecord(id, VersionControlProviderType.GITLAB,
                ChangeRequestRecord.key(VersionControlProviderType.GITLAB, repositoryId, mergeRequest.iid()),
                attributes.discussionId(), attributes.id(), request.user() == null ? null : request.user().id(),
                request.user() == null ? null : request.user().username(), attributes.note(),
                Boolean.TRUE.equals(attributes.system()) ? "system" : "published", true,
                Boolean.TRUE.equals(attributes.resolved()), Boolean.TRUE.equals(attributes.system())
                || attributes.discussionId() == null,
                "delete".equals(attributes.action()), "gitlab", updatedAt, updatedAt, now, null, List.of());
        syncService.synchronize(comment);
    }

    private ChangeRequestSync.Action action(final GitLabWebhookRequest.Attributes attributes) {
        if ("merged".equals(attributes.state())) {
            return ChangeRequestSync.Action.MERGED;
        }
        if ("closed".equals(attributes.state())) {
            return ChangeRequestSync.Action.CLOSED;
        }
        return "reopen".equalsIgnoreCase(attributes.action())
                ? ChangeRequestSync.Action.REOPENED : ChangeRequestSync.Action.UPSERT;
    }

    private void verifyToken(final String token) {
        final var supplied = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        final var expected = configuration.webhookSecret().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(supplied, expected)) {
            throw ArnoldError.raise(ArnoldErrorCode.AUTH_ERROR).build();
        }
    }

    private long timestamp(final String value, final long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return fallback;
        }
    }

    private List<Long> ids(final List<GitLabWebhookRequest.User> users) {
        return users == null ? List.of() : users.stream().map(GitLabWebhookRequest.User::id).toList();
    }

    private String authorName(final GitLabWebhookRequest request) {
        return request.user() != null && request.attributes().authorId() != null
                && request.user().id() == request.attributes().authorId()
                ? name(request.user()) : null;
    }

    private List<String> names(final List<GitLabWebhookRequest.User> users) {
        return users == null ? List.of() : users.stream().map(this::name).toList();
    }

    private List<String> states(final List<GitLabWebhookRequest.User> users) {
        return users == null ? List.of() : users.stream()
                .map(user -> user.state() == null ? "unreviewed" : user.state()).toList();
    }

    private String name(final GitLabWebhookRequest.User user) {
        return user.name() == null || user.name().isBlank() ? user.username() : user.name();
    }

    private boolean isDraft(final String title) {
        return title != null && (title.startsWith("Draft:") || title.startsWith("WIP:"));
    }

    private <T> T required(final T value) {
        if (value == null) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
        return value;
    }
}
