package com.phonepe.arnold.core.config;

import com.livetheoogway.crudstore.aerospike.NamespaceSet;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public record AerospikeStorageConfiguration(
        @NotNull
        @NotEmpty NamespaceSet userSet,
        @NotNull
        @NotEmpty NamespaceSet reviewSet,
        @NotNull
        @NotEmpty NamespaceSet changeRequestSet,
        @NotNull
        @NotEmpty NamespaceSet mergeQueueSet,
        @NotNull
        @NotEmpty NamespaceSet commentSet,
        @NotNull
        @NotEmpty NamespaceSet webhookDeliverySet,
        @NotNull
        @NotEmpty NamespaceSet projectionEventSet,
        @NotNull
        @NotEmpty NamespaceSet analysisRunSet,
        @NotNull
        @NotEmpty NamespaceSet versionControlCredentialSet,
        @NotNull
        @NotEmpty NamespaceSet oauthStateSet,
        @NotNull
        @NotEmpty NamespaceSet userChangeRequestSet,
        @NotNull
        @NotEmpty NamespaceSet userSyncStateSet,
        @NotNull
        @NotEmpty NamespaceSet providerIdentityLinkSet,
        @NotNull
        @NotEmpty NamespaceSet notificationSet,
        @NotNull
        @NotEmpty NamespaceSet mergeRequestGroupSet,
        @NotNull
        @NotEmpty NamespaceSet mergeRequestGroupV2Set,
        @NotNull
        @NotEmpty NamespaceSet clusterControlSet,
        @NotNull
        @NotEmpty NamespaceSet personaSet,
        @NotNull
        @NotEmpty NamespaceSet groupAnalysisRunSet,
        NamespaceSet runnerJobSet
) {
    /**
     * Runner job queue set; defaults to {@code arnold-runner-job-v1} in the review namespace.
     */
    public NamespaceSet runnerJobSetOrDefault() {
        return runnerJobSet != null ? runnerJobSet : new NamespaceSet(reviewSet.namespace(), "arnold-runner-job-v1");
    }
}
