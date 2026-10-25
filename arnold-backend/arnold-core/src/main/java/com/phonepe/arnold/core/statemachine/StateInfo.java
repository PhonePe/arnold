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

package com.phonepe.arnold.core.statemachine;

/**
 * Immutable snapshot of an entity's position in a state machine: the {@code state} it currently
 * occupies and the {@code data} associated with that state (e.g. analysis-run metadata while
 * {@code ANALYSING}). Actions return a new {@code StateInfo} to describe the post-transition state.
 */
public record StateInfo<S extends Enum<S>, D>(
        S state,
        D data
) {
    public static <S extends Enum<S>, D> StateInfo<S, D> of(final S state, final D data) {
        return new StateInfo<>(state, data);
    }

    public static <S extends Enum<S>, D> StateInfo<S, D> of(final S state) {
        return new StateInfo<>(state, null);
    }
}
