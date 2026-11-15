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

package com.phonepe.arnold.core.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.arnold.core.config.AerospikeStorageConfiguration;
import com.phonepe.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.arnold.core.model.review.ReviewEvent;
import com.phonepe.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.arnold.core.model.review.ReviewState;
import com.phonepe.arnold.core.model.review.ReviewStateData;
import com.phonepe.arnold.core.review.ReviewStateMachineFactory;
import com.phonepe.arnold.core.review.ReviewStateStores;
import com.phonepe.arnold.core.statemachine.ActionResolver;
import com.phonepe.arnold.core.statemachine.GuiceActionResolver;
import com.phonepe.arnold.core.statemachine.StateMachine;
import com.phonepe.arnold.core.statemachine.store.StateStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeReviewStateStore;
import com.phonepe.arnold.core.storage.ClusterControlStore;
import com.phonepe.arnold.core.storage.NotificationStore;
import com.phonepe.arnold.core.storage.ReviewStateStore;
import com.phonepe.arnold.core.storage.UserChangeRequestStore;
import com.phonepe.arnold.core.storage.UserSyncStateStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeClusterControlStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeNotificationStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeUserChangeRequestStore;
import com.phonepe.arnold.core.storage.aerospike.AerospikeUserSyncStateStore;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import javax.inject.Singleton;

public class DBModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ActionResolver.class).to(GuiceActionResolver.class);
        bind(ClusterControlStore.class).to(AerospikeClusterControlStore.class);
        bind(NotificationStore.class).to(AerospikeNotificationStore.class);
        bind(UserSyncStateStore.class).to(AerospikeUserSyncStateStore.class);
        bind(UserChangeRequestStore.class).to(AerospikeUserChangeRequestStore.class);
        bind(ReviewStateStore.class).to(AerospikeReviewStateStore.class);
    }

    @Provides
    @Singleton
    @AerospikeSets.Review
    public NamespaceSet reviewSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.reviewSet();
    }

    @Provides
    @Singleton
    @AerospikeSets.User
    public NamespaceSet userSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.userSet();
    }

    @Provides
    @Singleton
    @AerospikeSets.ChangeRequest
    public NamespaceSet changeRequestSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.changeRequestSet();
    }

    @Provides
    @Singleton
    @AerospikeSets.MergeQueue
    public NamespaceSet mergeQueueSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.mergeQueueSet();
    }

    @Provides
    @Singleton
    @AerospikeSets.Comment
    public NamespaceSet commentSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.commentSet();
    }

    @Provides
    @Singleton
    @AerospikeSets.WebhookDelivery
    public NamespaceSet webhookDeliverySet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.webhookDeliverySet();
    }

    @Provides
    @Singleton
    @AerospikeSets.ProjectionEvent
    public NamespaceSet projectionEventSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.projectionEventSet();
    }

    @Provides
    @Singleton
    @AerospikeSets.AnalysisRun
    public NamespaceSet analysisRunSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.analysisRunSet();
    }

    @Provides
    @Singleton
    @AerospikeSets.VersionControlCredential
    public NamespaceSet versionControlCredentialSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.versionControlCredentialSet();
    }

    @Provides
    @Singleton
    @AerospikeSets.RunnerJob
    public NamespaceSet runnerJobSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.runnerJobSetOrDefault();
    }

    @Provides
    @Singleton
    @AerospikeSets.OAuthState
    public NamespaceSet oauthStateSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.oauthStateSet();
    }

    @Provides
    @Singleton
    @AerospikeSets.UserChangeRequest
    public NamespaceSet userChangeRequestSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.userChangeRequestSet();
    }

    @Provides
    @Singleton
    @AerospikeSets.UserSyncState
    public NamespaceSet userSyncStateSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.userSyncStateSet();
    }

    @Provides
    @Singleton
    @AerospikeSets.ProviderIdentityLink
    public NamespaceSet providerIdentityLinkSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.providerIdentityLinkSet();
    }

    @Provides
    @Singleton
    @AerospikeSets.Notification
    public NamespaceSet notificationSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.notificationSet();
    }

    @Provides
    @Singleton
    @AerospikeSets.MergeRequestGroup
    public NamespaceSet mergeRequestGroupSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.mergeRequestGroupSet();
    }

    @Provides
    @Singleton
    @AerospikeSets.ClusterControl
    public NamespaceSet clusterControlSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.clusterControlSet();
    }

    @Provides
    @Singleton
    @AerospikeSets.Persona
    public NamespaceSet personaSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.personaSet();
    }

    /** Adapts the Aerospike-backed crud-store into the engine's version-CAS {@link StateStore}. */
    @Provides
    @Singleton
    public StateStore<ReviewState, ReviewStateData> reviewStateStore(final AerospikeReviewStateStore store) {
        return ReviewStateStores.over(store);
    }

    /** The concrete MR review state machine, wired over the durable store and Guice-resolved actions. */
    @Provides
    @Singleton
    public StateMachine<ReviewState, ReviewEvent, ReviewStateData, ReviewEventPayload> reviewStateMachine(
            final StateStore<ReviewState, ReviewStateData> stateStore,
            final ActionResolver actionResolver) {
        return new ReviewStateMachineFactory(stateStore, actionResolver).create();
    }
}
