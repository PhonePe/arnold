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

package com.phonepe.arnold.core.review;

import com.livetheoogway.crudstore.core.ConcurrentStore;
import com.phonepe.arnold.core.model.review.ArnoldReview;
import com.phonepe.arnold.core.model.review.ReviewState;
import com.phonepe.arnold.core.model.review.ReviewStateData;
import com.phonepe.arnold.core.statemachine.StateInfo;
import com.phonepe.arnold.core.statemachine.store.StateStore;
import com.phonepe.arnold.core.storage.ConcurrentStoreStateStore;

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
