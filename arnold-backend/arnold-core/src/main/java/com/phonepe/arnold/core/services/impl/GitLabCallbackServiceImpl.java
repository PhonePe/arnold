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

import com.phonepe.arnold.core.config.GitLabSyncConfiguration;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.comment.CommentRecord;
import com.phonepe.arnold.core.model.sync.WebhookDelivery;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.ChangeRequestSync;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.provider.gitlab.GitLabClient;
import com.phonepe.arnold.core.services.ChangeRequestService;
import com.phonepe.arnold.core.services.GitLabCallbackService;
import com.phonepe.arnold.core.services.GitLabOAuthService;
import com.phonepe.arnold.core.services.GitLabWebhookFanoutService;
import com.phonepe.arnold.core.services.UserMergeRequestService;
import com.phonepe.arnold.core.services.UserSyncControlService;
import com.phonepe.arnold.core.services.VersionControlSyncService;
import com.phonepe.arnold.core.storage.aerospike.AerospikeCommentStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeWebhookDeliveryStore;
import com.phonepe.arnold.models.callback.CallbackResponse;
import com.phonepe.arnold.models.callback.GitLabWebhookRequest;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Function;

@Singleton
@Slf4j
public class GitLabCallbackServiceImpl implements GitLabCallbackService {
    private static final DateTimeFormatter WEBHOOK_TIMESTAMP = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .appendLiteral(' ').appendZoneOrOffsetId().toFormatter(Locale.ROOT);
    private static final long DELIVERY_LEASE_MS = 300_000;
    private final GitLabSyncConfiguration configuration;
    private final AerospikeWebhookDeliveryStore deliveryStore;
    private final VersionControlSyncService syncService;
    private final ChangeRequestService changeRequestService;
    private final GitLabWebhookFanoutService webhookFanoutService;
    private final UserSyncControlService syncControlService;
    private final AerospikeCommentStore commentStore;
    private final GitLabClient gitLabClient;
    private final GitLabOAuthService oauthService;
    private final UserMergeRequestService userMergeRequestService;

    @Inject
    public GitLabCallbackServiceImpl(final @Config GitLabSyncConfiguration configuration,
                                     final AerospikeWebhookDeliveryStore deliveryStore,
                                     final VersionControlSyncService syncService,
                                     final ChangeRequestService changeRequestService,
                                     final GitLabWebhookFanoutService webhookFanoutService,
                                     final UserSyncControlService syncControlService,
                                     final AerospikeCommentStore commentStore,
                                     final GitLabClient gitLabClient,
                                     final GitLabOAuthService oauthService,
                                     final UserMergeRequestService userMergeRequestService) {
        this.configuration = configuration;
        this.deliveryStore = deliveryStore;
        this.syncService = syncService;
        this.changeRequestService = changeRequestService;
        this.webhookFanoutService = webhookFanoutService;
        this.syncControlService = syncControlService;
        this.commentStore = commentStore;
        this.gitLabClient = gitLabClient;
        this.oauthService = oauthService;
        this.userMergeRequestService = userMergeRequestService;
    }

    @Override
    public CallbackResponse process(final String token, final String deliveryId, final String eventType,
                                    final GitLabWebhookRequest request) {
        verifyToken(token);
        if (deliveryId == null || deliveryId.isBlank()) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
        log.info("GitLab webhook received deliveryId:{} eventType:{}", deliveryId, eventType);
        final var receivedAt = System.currentTimeMillis();
        if (!claimDelivery(deliveryId, eventType, receivedAt)) {
            return new CallbackResponse(deliveryId, "duplicate");
        }
        try {
            processEvent(request);
            if (syncControlService != null) {
                syncControlService.callbackReceived();
            }
            finishDelivery(deliveryId, eventType, receivedAt, "processed");
            log.info("GitLab webhook processed deliveryId:{} eventType:{}", deliveryId, eventType);
            return new CallbackResponse(deliveryId, "processed");
        } catch (RuntimeException failure) {
            try {
                finishDelivery(deliveryId, eventType, receivedAt, "failed");
            } catch (RuntimeException recoveryFailure) {
                failure.addSuppressed(recoveryFailure);
            }
            throw failure;
        }
    }

    private boolean claimDelivery(final String id, final String eventType, final long now) {
        final var current = deliveryStore.getWithVersion(id);
        final var processing = new WebhookDelivery(id, eventType, "processing", now, 0);
        if (current.isEmpty()) {
            deliveryStore.create(processing);
            return true;
        }
        final var delivery = current.get().value();
        if ("processed".equals(delivery.status())) {
            return false;
        }
        if ("processing".equals(delivery.status()) && now - delivery.receivedAt() < DELIVERY_LEASE_MS) {
            throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        }
        deliveryStore.checkAndUpdate(processing, current.get().version());
        return true;
    }

    private void finishDelivery(final String id, final String eventType, final long receivedAt, final String status) {
        final var current = deliveryStore.getWithVersion(id).orElseThrow();
        if (current.value().receivedAt() != receivedAt || !"processing".equals(current.value().status())) {
            throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
        }
        deliveryStore.checkAndUpdate(new WebhookDelivery(id, eventType, status, receivedAt,
                System.currentTimeMillis()), current.version());
    }

    private void processEvent(final GitLabWebhookRequest request) {
        required(request);
        final var kind = request.objectKind() == null ? "" : request.objectKind().toLowerCase(Locale.ROOT);
        switch (kind) {
            case "merge_request" -> processMergeRequest(request);
            case "note" -> processNote(request);
            case "pipeline" -> processPipeline(request);
            default -> throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
    }

    private void processMergeRequest(final GitLabWebhookRequest request) {
        final var attributes = required(request.attributes());
        final var project = required(request.project());
        if (project.id() <= 0 || attributes.iid() <= 0) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
        final var repositoryId = Long.toString(project.id());
        final var id = ChangeRequestRecord.key(VersionControlProviderType.GITLAB, repositoryId, attributes.iid());
        final var previous = changeRequestService.record(id).orElse(null);
        final var now = System.currentTimeMillis();
        final var updatedAt = timestamp(attributes.updatedAt(), prior(previous, ChangeRequestRecord::providerUpdatedAt, now));
        final var state = value(attributes.state(), prior(previous, ChangeRequestRecord::state, "opened"));
        final var title = value(attributes.title(), prior(previous, ChangeRequestRecord::title, null));
        final var mergeStatus = attributes.detailedMergeStatus() == null || attributes.detailedMergeStatus().isBlank()
                ? value(attributes.mergeStatus(), prior(previous, ChangeRequestRecord::mergeStatus, null))
                : attributes.detailedMergeStatus();
        final var draft = value(attributes.draft(), attributes.title() == null
                ? prior(previous, ChangeRequestRecord::draft, false) : isDraft(title));
        final var current = new ChangeRequestRecord(id, VersionControlProviderType.GITLAB, repositoryId,
                attributes.id() == 0 ? prior(previous, ChangeRequestRecord::providerId, null) : Long.toString(attributes.id()),
                attributes.iid(), value(project.pathWithNamespace(), prior(previous, ChangeRequestRecord::repositoryPath, null)),
                title, attributes.description() == null && (request.changes() == null || !request.changes().has("description"))
                        ? prior(previous, ChangeRequestRecord::description, null) : attributes.description(),
                value(attributes.url(), prior(previous, ChangeRequestRecord::webUrl, null)),
                value(attributes.sourceBranch(), prior(previous, ChangeRequestRecord::sourceBranch, null)),
                value(attributes.targetBranch(), prior(previous, ChangeRequestRecord::targetBranch, null)),
                attributes.lastCommit() == null ? prior(previous, ChangeRequestRecord::headSha, null) : attributes.lastCommit().id(),
                state, mergeStatus, draft, value(attributes.authorId(), prior(previous, ChangeRequestRecord::authorId, null)),
                value(authorName(request), prior(previous, ChangeRequestRecord::authorName, null)),
                request.assignees() == null ? prior(previous, ChangeRequestRecord::assigneeIds, List.of()) : ids(request.assignees()),
                request.assignees() == null ? prior(previous, ChangeRequestRecord::assigneeNames, List.of()) : names(request.assignees()),
                request.reviewers() == null ? prior(previous, ChangeRequestRecord::reviewerIds, List.of()) : ids(request.reviewers()),
                request.reviewers() == null ? prior(previous, ChangeRequestRecord::reviewerNames, List.of()) : names(request.reviewers()),
                reviewerStates(request, previous),
                request.labels() == null ? prior(previous, ChangeRequestRecord::labels, List.of())
                        : request.labels().stream().map(GitLabWebhookRequest.Label::title).toList(),
                timestamp(attributes.createdAt(), prior(previous, ChangeRequestRecord::createdAt, updatedAt)),
                completionTime(state, "merged", attributes.mergedAt(), prior(previous, ChangeRequestRecord::mergedAt, 0L), updatedAt),
                completionTime(state, "closed", attributes.closedAt(), prior(previous, ChangeRequestRecord::closedAt, 0L), updatedAt),
                value(attributes.hasConflicts(), attributes.mergeStatus() == null && attributes.detailedMergeStatus() == null
                        ? prior(previous, ChangeRequestRecord::hasConflicts, false) : "conflict".equalsIgnoreCase(mergeStatus)),
                value(attributes.blockingDiscussionsResolved(), prior(previous, ChangeRequestRecord::blockingDiscussionsResolved, false)),
                updatedAt, now, prior(previous, ChangeRequestRecord::projectArchived, false));
        if (synchronize(previous, current, request.user() == null ? null : request.user().username())
                && webhookFanoutService != null && request.changes() != null) {
            final var reviewers = request.changes().path("reviewers");
            final var updated = reviewers.isArray() && reviewers.size() == 2 ? reviewers.get(1) : reviewers.path("current");
            final var requested = new HashSet<Long>();
            updated.forEach(reviewer -> {
                if (reviewer.path("re_requested").asBoolean()) {
                    requested.add(reviewer.path("id").asLong());
                }
            });
            if (!requested.isEmpty()) {
                webhookFanoutService.reviewRequested(current, requested);
            }
        }
    }

    private boolean synchronize(final ChangeRequestRecord previous, final ChangeRequestRecord current, final String actor) {
        final var action = "merged".equalsIgnoreCase(current.state()) ? ChangeRequestSync.Action.MERGED
                : "closed".equalsIgnoreCase(current.state()) ? ChangeRequestSync.Action.CLOSED
                : previous != null && "closed".equalsIgnoreCase(previous.state()) && current.isOpen()
                ? ChangeRequestSync.Action.REOPENED : ChangeRequestSync.Action.UPSERT;
        if (!syncService.synchronize(new ChangeRequestSync(current, action, actor))) {
            return false;
        }
        if (webhookFanoutService != null) {
            webhookFanoutService.synchronize(previous, current);
        }
        return true;
    }

    private void processPipeline(final GitLabWebhookRequest request) {
        if (request.mergeRequest() == null) {
            return;
        }
        final var project = required(request.project());
        final var iid = request.mergeRequest().iid();
        final var projectId = value(request.mergeRequest().targetProjectId(), project.id());
        if (projectId <= 0 || iid <= 0) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
        final var id = ChangeRequestRecord.key(VersionControlProviderType.GITLAB, Long.toString(projectId), iid);
        final var previous = changeRequestService.record(id).orElse(null);
        if (previous == null) {
            return;
        }
        RuntimeException failure = null;
        ChangeRequestRecord refreshed = null;
        for (final var association : userMergeRequestService.associationsForMergeRequest(id)) {
            if (!association.active()) {
                continue;
            }
            try {
                final var credential = oauthService.validCredential(association.userId());
                refreshed = gitLabClient.getMergeRequest(projectId, iid, credential.accessToken());
                break;
            } catch (RuntimeException unavailable) {
                failure = unavailable;
            }
        }
        if (refreshed != null) {
            synchronize(previous, refreshed.withProjectArchived(previous.projectArchived()), null);
        } else if (failure != null) {
            throw failure;
        }
    }

    private void processNote(final GitLabWebhookRequest request) {
        final var attributes = required(request.attributes());
        if (!"MergeRequest".equals(attributes.noteableType())) {
            return;
        }
        final var project = required(request.project());
        final var mergeRequest = required(request.mergeRequest());
        if (project.id() <= 0 || mergeRequest.iid() <= 0 || attributes.id() <= 0) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
        final var now = System.currentTimeMillis();
        final var repositoryId = Long.toString(project.id());
        final var id = CommentRecord.key(VersionControlProviderType.GITLAB, repositoryId, mergeRequest.iid(), attributes.id());
        final var previous = commentStore.get(id).orElse(null);
        final var updatedAt = timestamp(attributes.updatedAt(), prior(previous, CommentRecord::providerUpdatedAt, now));
        final var authorId = value(attributes.authorId(), prior(previous, CommentRecord::authorId, null));
        final var username = request.user() != null && Objects.equals(authorId, request.user().id())
                ? request.user().username() : previous != null && Objects.equals(authorId, previous.authorId())
                ? previous.authorUsername() : null;
        final var discussionId = value(attributes.discussionId(), prior(previous, CommentRecord::discussionId, null));
        final var comment = new CommentRecord(id, VersionControlProviderType.GITLAB,
                ChangeRequestRecord.key(VersionControlProviderType.GITLAB, repositoryId, mergeRequest.iid()),
                discussionId, attributes.id(), authorId, username,
                value(attributes.note(), prior(previous, CommentRecord::body, null)),
                attributes.system() == null ? prior(previous, CommentRecord::state, "published")
                        : attributes.system() ? "system" : "published",
                value(attributes.resolvable(), prior(previous, CommentRecord::resolvable, false)),
                value(attributes.resolved(), prior(previous, CommentRecord::resolved, false)),
                value(attributes.individualNote(), prior(previous, CommentRecord::individualNote, discussionId == null)),
                "delete".equals(attributes.action()) || prior(previous, CommentRecord::deleted, false),
                prior(previous, CommentRecord::source, "gitlab"),
                timestamp(attributes.createdAt(), prior(previous, CommentRecord::createdAt, updatedAt)), updatedAt, now,
                prior(previous, CommentRecord::position, null), prior(previous, CommentRecord::suggestions, List.of()));
        syncService.synchronize(comment);
    }

    private long completionTime(final String state, final String completedState, final String supplied,
                                final long previous, final long updatedAt) {
        return completedState.equalsIgnoreCase(state) ? timestamp(supplied, previous > 0 ? previous : updatedAt) : 0;
    }

    private <T, R> R prior(final T previous, final Function<T, R> getter, final R fallback) {
        return previous == null ? fallback : getter.apply(previous);
    }

    private <T> T value(final T supplied, final T fallback) {
        return supplied == null ? fallback : supplied;
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
            try {
                return ZonedDateTime.parse(value, WEBHOOK_TIMESTAMP).toInstant().toEpochMilli();
            } catch (DateTimeParseException invalid) {
                return fallback;
            }
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

    private List<String> reviewerStates(final GitLabWebhookRequest request, final ChangeRequestRecord previous) {
        final var reviewerIds = request.reviewers() == null
                ? prior(previous, ChangeRequestRecord::reviewerIds, List.<Long>of()) : ids(request.reviewers());
        final var changes = request.changes() == null ? null : request.changes().path("reviewers");
        final var changed = changes == null ? null
                : changes.isArray() && changes.size() == 2 ? changes.get(1) : changes.path("current");
        return reviewerIds.stream().map(id -> {
            if (changed != null) {
                for (final var reviewer : changed) {
                    if (reviewer.path("id").asLong() == id) {
                        if (reviewer.path("re_requested").asBoolean()) {
                            return "unreviewed";
                        }
                        if (reviewer.hasNonNull("state")) {
                            return reviewer.path("state").asText();
                        }
                    }
                }
            }
            if (request.reviewers() != null) {
                final var state = request.reviewers().get(reviewerIds.indexOf(id)).state();
                if (state != null) {
                    return state;
                }
            }
            final var index = previous == null ? -1 : previous.reviewerIds().indexOf(id);
            return index >= 0 && index < previous.reviewerStates().size()
                    ? previous.reviewerStates().get(index) : "unreviewed";
        }).toList();
    }

    private String name(final GitLabWebhookRequest.User user) {
        return user.name() == null || user.name().isBlank() ? user.username() : user.name();
    }

    private boolean isDraft(final String title) {
        return title != null && title.matches("(?i)^(?:draft:|wip:|\\[draft\\]|\\[wip\\]|\\(draft\\)|\\(wip\\)).*");
    }

    private <T> T required(final T value) {
        if (value == null) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).build();
        }
        return value;
    }
}
