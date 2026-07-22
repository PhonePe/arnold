package com.phonepe.devx.arnold.core.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.devx.arnold.core.config.AerospikeStorageConfiguration;
import com.phonepe.devx.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.devx.arnold.core.model.review.ReviewEvent;
import com.phonepe.devx.arnold.core.model.review.ReviewEventPayload;
import com.phonepe.devx.arnold.core.model.review.ReviewState;
import com.phonepe.devx.arnold.core.model.review.ReviewStateData;
import com.phonepe.devx.arnold.core.review.ReviewStateMachineFactory;
import com.phonepe.devx.arnold.core.review.ReviewStateStores;
import com.phonepe.devx.arnold.core.statemachine.ActionResolver;
import com.phonepe.devx.arnold.core.statemachine.GuiceActionResolver;
import com.phonepe.devx.arnold.core.statemachine.StateMachine;
import com.phonepe.devx.arnold.core.statemachine.store.StateStore;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeReviewStateStore;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import javax.inject.Singleton;

public class DBModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ActionResolver.class).to(GuiceActionResolver.class);
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
    @AerospikeSets.OAuthState
    public NamespaceSet oauthStateSet(final @Config AerospikeStorageConfiguration storageConfiguration) {
        return storageConfiguration.oauthStateSet();
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
