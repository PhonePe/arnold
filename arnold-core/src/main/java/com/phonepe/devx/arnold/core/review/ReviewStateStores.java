package com.phonepe.devx.arnold.core.review;

import com.livetheoogway.crudstore.core.ConcurrentStore;
import com.phonepe.devx.arnold.core.statemachine.StateInfo;
import com.phonepe.devx.arnold.core.statemachine.store.StateStore;
import com.phonepe.devx.arnold.core.storage.ConcurrentStoreStateStore;

/**
 * Maps between the persisted {@link ArnoldReview} record and the engine's {@link StateInfo}, and builds
 * the review {@link StateStore} over a crud-store {@link ConcurrentStore} (e.g. the Aerospike-backed
 * {@code AerospikeReviewStateStore}). Isolating the mapping here keeps the generic adapter domain-free.
 */
public final class ReviewStateStores {

    private ReviewStateStores() {
    }

    public static ArnoldReview toItem(final String mrId, final StateInfo<ReviewState, ReviewStateData> stateInfo) {
        return new ArnoldReview(mrId, stateInfo.state(), stateInfo.data());
    }

    public static StateInfo<ReviewState, ReviewStateData> toStateInfo(final ArnoldReview review) {
        return StateInfo.of(review.state(), review.data());
    }

    public static <V> StateStore<ReviewState, ReviewStateData> over(final ConcurrentStore<ArnoldReview, V> store) {
        return new ConcurrentStoreStateStore<>(store, ReviewStateStores::toItem, ReviewStateStores::toStateInfo);
    }
}
