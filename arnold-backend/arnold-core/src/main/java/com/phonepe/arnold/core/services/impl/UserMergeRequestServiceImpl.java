package com.phonepe.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.review.ArnoldReview;
import com.phonepe.arnold.core.model.sync.SyncStatus;
import com.phonepe.arnold.core.model.sync.UserSyncState;
import com.phonepe.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.arnold.core.model.vcs.MergeRequestRole;
import com.phonepe.arnold.core.model.vcs.UserChangeRequest;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.ChangeRequestService;
import com.phonepe.arnold.core.services.UserMergeRequestService;
import com.phonepe.arnold.core.storage.ReviewStateStore;
import com.phonepe.arnold.core.storage.UserChangeRequestStore;
import com.phonepe.arnold.core.storage.UserSyncStateStore;
import com.phonepe.arnold.models.mergerequest.UserMergeRequestPageResponse;
import com.phonepe.arnold.models.mergerequest.UserMergeRequestResponse;
import com.phonepe.arnold.models.sync.UserSyncStatusResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class UserMergeRequestServiceImpl implements UserMergeRequestService {
    private static final int MAX_UPDATE_ATTEMPTS = 8;

    private final Clock clock;
    private final UserChangeRequestStore associationStore;
    private final ReviewStateStore reviewStore;
    private final UserSyncStateStore syncStateStore;
    private final ChangeRequestService changeRequestService;

    @Inject
    public UserMergeRequestServiceImpl(final UserChangeRequestStore associationStore,
                                       final ReviewStateStore reviewStore,
                                       final UserSyncStateStore syncStateStore,
                                       final ChangeRequestService changeRequestService) {
        this(associationStore, reviewStore, syncStateStore, changeRequestService, Clock.systemUTC());
    }

    UserMergeRequestServiceImpl(final UserChangeRequestStore associationStore,
                                final ReviewStateStore reviewStore,
                                final UserSyncStateStore syncStateStore,
                                final ChangeRequestService changeRequestService,
                                final Clock clock) {
        this.clock = clock;
        this.associationStore = associationStore;
        this.reviewStore = reviewStore;
        this.syncStateStore = syncStateStore;
        this.changeRequestService = changeRequestService;
    }

    @Override
    public List<UserChangeRequest> associationsForUser(final String userId) {
        return associationStore.findByUser(userId);
    }

    @Override
    public List<UserChangeRequest> associationsForMergeRequest(final String mergeRequestId) {
        return associationStore.findByMergeRequest(mergeRequestId);
    }

    @Override
    public UserChangeRequest upsert(final String userId,
                                    final ChangeRequestRecord mergeRequest,
                                    final Set<MergeRequestRole> roles,
                                    final long seenAt) {
        final var id = UserChangeRequest.key(userId, mergeRequest.id());
        for (var attempt = 0; attempt < MAX_UPDATE_ATTEMPTS; attempt++) {
            final var current = associationStore.getVersioned(id);
            final var firstSeenAt = current.map(item -> item.value().firstSeenAt())
                    .orElse(seenAt);
            final var association = new UserChangeRequest(
                    id, userId, mergeRequest.id(), roles, !roles.isEmpty() && !mergeRequest.projectArchived(),
                    isOpen(mergeRequest), firstSeenAt, seenAt, mergeRequest.providerUpdatedAt());

            if (current.isEmpty()) {
                try {
                    associationStore.create(association);
                    return association;
                } catch (RuntimeException race) {
                    continue;
                }
            }

            try {
                associationStore.update(association, current.get().version());
                return association;
            } catch (ConcurrentUpdateException conflict) {
                // Re-read and retry.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.CONFLICT_ERROR).build();
    }

    @Override
    public UserChangeRequest deactivate(final UserChangeRequest association,
                                        final ChangeRequestRecord mergeRequest,
                                        final long seenAt) {
        return upsert(association.userId(), mergeRequest, Set.of(), seenAt);
    }

    @Override
    public UserMergeRequestPageResponse list(final String userId,
                                             final String requestedRole,
                                             final int requestedLimit,
                                             final String cursor,
                                             final String state) {
        if (!"open".equals(state) && !"merged".equals(state)) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).message("Invalid merge request state").build();
        }
        final var merged = "merged".equals(state);
        final var cutoff = clock.millis() - Duration.ofDays(30).toMillis();
        final var role = parseRole(requestedRole);
        final var limit = Math.max(1, Math.min(100, requestedLimit));
        final var offset = decodeCursor(cursor);
        final var associations = associationsForUser(userId).stream()
                .filter(UserChangeRequest::active)
                .filter(item -> merged || item.open())
                .filter(item -> role == null || item.roles().contains(role))
                .toList();
        final var records = changeRequestService.records(
                associations.stream().map(UserChangeRequest::mergeRequestId).toList());
        final var ordered = associations.stream()
                .filter(item -> records.containsKey(item.mergeRequestId())
                        && !records.get(item.mergeRequestId()).projectArchived())
                .filter(item -> !merged || ("merged".equalsIgnoreCase(records.get(item.mergeRequestId()).state())
                        && records.get(item.mergeRequestId()).mergedAt() >= cutoff))
                .sorted(Comparator.comparingLong((UserChangeRequest item) -> merged
                        ? records.get(item.mergeRequestId()).mergedAt()
                        : records.get(item.mergeRequestId()).providerUpdatedAt()).reversed()
                        .thenComparing(UserChangeRequest::mergeRequestId))
                .toList();
        final var pageAssociations = ordered.stream().skip(offset).limit(limit).toList();
        final var summaries = summaries(userId, pageAssociations.stream()
                .map(UserChangeRequest::mergeRequestId).toList());
        final var items = pageAssociations.stream().map(UserChangeRequest::mergeRequestId)
                .map(summaries::get).filter(java.util.Objects::nonNull).toList();
        final var next = offset + items.size() < ordered.size() ? encodeCursor(offset + items.size()) : null;
        return new UserMergeRequestPageResponse(items, next, syncResponses(userId));
    }

    @Override
    public Map<String, UserMergeRequestResponse> summaries(final String userId,
                                                           final Collection<String> mergeRequestIds) {
        if (mergeRequestIds.isEmpty()) {
            return Map.of();
        }
        final var ids = mergeRequestIds.stream().distinct().toList();
        final var records = changeRequestService.records(ids);
        final Map<String, ArnoldReview> reviews = reviewStore.get(ids);
        final var associations = associationsForUser(userId).stream()
                .filter(item -> ids.contains(item.mergeRequestId()))
                .collect(Collectors.toMap(UserChangeRequest::mergeRequestId, item -> item, (left, right) -> right));
        final var result = new LinkedHashMap<String, UserMergeRequestResponse>();
        ids.forEach(id -> {
            final var record = records.get(id);
            if (record == null || record.projectArchived()) {
                return;
            }
            final var association = associations.get(id);
            final var roles = association == null ? Set.<String>of() : association.roles().stream()
                                                                       .map(value -> value.name().toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
            final var review = reviews.get(id);
            final var reviewState = review == null ? record.state().toUpperCase(Locale.ROOT) : review.state().name();
            result.put(id, new UserMergeRequestResponse(changeRequestService.response(record), roles, reviewState,
                    record.isBlocked()));
        });
        return Map.copyOf(result);
    }

    private Map<String, UserSyncStatusResponse> syncResponses(final String userId) {
        return Arrays.stream(VersionControlProviderType.values()).collect(Collectors.toUnmodifiableMap(
                Enum::name, provider -> syncResponse(provider, userId)));
    }

    private UserSyncStatusResponse syncResponse(final VersionControlProviderType provider, final String userId) {
        final var state = syncStateStore.get(UserSyncState.key(provider, userId))
                .orElseGet(() -> UserSyncState.neverSynced(provider, userId));
        return new UserSyncStatusResponse(state.provider().name(), state.status().name(), state.lastAttemptAt(),
                state.lastSucceededAt(), state.nextDueAt(), state.failure(), state.status() == SyncStatus.FAILED);
    }

    private MergeRequestRole parseRole(final String value) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return MergeRequestRole.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).message("Invalid merge request role").build();
        }
    }

    private int decodeCursor(final String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            final var offset = Integer.parseInt(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
            if (offset < 0) throw new IllegalArgumentException("Negative cursor");
            return offset;
        } catch (RuntimeException invalid) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR).message("Invalid cursor").build();
        }
    }

    private String encodeCursor(final int offset) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Integer.toString(offset).getBytes(StandardCharsets.UTF_8));
    }

    private boolean isOpen(final ChangeRequestRecord mergeRequest) {
        return "opened".equalsIgnoreCase(mergeRequest.state()) || "open".equalsIgnoreCase(mergeRequest.state());
    }

}
