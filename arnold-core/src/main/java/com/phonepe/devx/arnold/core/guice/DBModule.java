package com.phonepe.devx.arnold.core.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.livetheoogway.crudstore.aerospike.NamespaceSet;
import com.phonepe.devx.arnold.core.config.AerospikeStorageConfiguration;
import com.phonepe.devx.arnold.core.guice.bindings.AerospikeSets;
import com.phonepe.devx.arnold.core.review.ReviewEvent;
import com.phonepe.devx.arnold.core.review.ReviewEventPayload;
import com.phonepe.devx.arnold.core.review.ReviewState;
import com.phonepe.devx.arnold.core.review.ReviewStateData;
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
        return storageConfiguration.reviewSet();
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

