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
        @NotEmpty NamespaceSet clusterControlSet,
        @NotNull
        @NotEmpty NamespaceSet personaSet,
        @NotNull
        @NotEmpty NamespaceSet groupAnalysisRunSet,
        NamespaceSet runnerJobSet,
        @NotNull
        @NotEmpty NamespaceSet flowJobUsageSet
) {
    /**
     * Runner job queue set; defaults to {@code arnold-runner-job-v1} in the review namespace.
     */
    public NamespaceSet runnerJobSetOrDefault() {
        return runnerJobSet != null ? runnerJobSet : new NamespaceSet(reviewSet.namespace(), "arnold-runner-job-v1");
    }
}
