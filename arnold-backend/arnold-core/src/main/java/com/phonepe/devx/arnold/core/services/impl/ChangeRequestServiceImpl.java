package com.phonepe.devx.arnold.core.services.impl;

import com.livetheoogway.crudstore.core.ConcurrentUpdateException;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.vcs.ChangeRequestRecord;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.provider.VersionControlProviders;
import com.phonepe.devx.arnold.core.services.ChangeRequestService;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeChangeRequestStore;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ChangeRequestServiceImpl implements ChangeRequestService {
    private static final int MAX_SYNC_ATTEMPTS = 8;

    private final AerospikeChangeRequestStore mergeRequestStore;
    private final VersionControlProviders providers;

    @Inject
    public ChangeRequestServiceImpl(final AerospikeChangeRequestStore mergeRequestStore,
                                    final VersionControlProviders providers) {
        this.mergeRequestStore = mergeRequestStore;
        this.providers = providers;
    }

    @Override
    public MergeRequestResponse get(final long projectId, final long iid, final String userId) {
        return mergeRequestStore.get(ChangeRequestRecord.key(VersionControlProviderType.GITLAB,
                        Long.toString(projectId), iid))
                .map(this::toResponse)
                .orElseThrow(() -> ArnoldError.raise(ArnoldErrorCode.NOT_FOUND_ERROR).build());
    }

    @Override
    public MergeRequestResponse resolve(final String url, final String userId) {
        return providers.get(VersionControlProviderType.GITLAB).resolveChangeRequest(url, userId);
    }

    @Override
    public boolean synchronize(final ChangeRequestRecord mergeRequest) {
        for (var attempt = 0; attempt < MAX_SYNC_ATTEMPTS; attempt++) {
            final var current = mergeRequestStore.getWithVersion(mergeRequest.id());
            if (current.isEmpty()) {
                try {
                    mergeRequestStore.create(mergeRequest);
                    return true;
                } catch (RuntimeException createRace) {
                    continue;
                }
            }
            if (current.get().value().providerUpdatedAt() > mergeRequest.providerUpdatedAt()) {
                return false;
            }
            try {
                mergeRequestStore.checkAndUpdate(mergeRequest, current.get().version());
                return true;
            } catch (ConcurrentUpdateException conflict) {
                // Retry against the latest Aerospike generation.
            }
        }
        throw ArnoldError.raise(ArnoldErrorCode.DB_ERROR).build();
    }

    private MergeRequestResponse toResponse(final ChangeRequestRecord mergeRequest) {
        return new MergeRequestResponse(mergeRequest.id(), Long.parseLong(mergeRequest.repositoryId()),
                Long.parseLong(mergeRequest.providerId()), mergeRequest.number(), mergeRequest.repositoryPath(),
                mergeRequest.title(), mergeRequest.description(),
                mergeRequest.webUrl(), mergeRequest.sourceBranch(), mergeRequest.targetBranch(), mergeRequest.headSha(),
                mergeRequest.state(), mergeRequest.mergeStatus(), mergeRequest.draft(), mergeRequest.authorId(),
                mergeRequest.assigneeIds(), mergeRequest.reviewerIds(), mergeRequest.labels(),
                mergeRequest.providerUpdatedAt(), mergeRequest.lastSyncedAt());
    }
}
