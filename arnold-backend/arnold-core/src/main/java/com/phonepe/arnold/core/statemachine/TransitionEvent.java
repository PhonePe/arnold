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

package com.phonepe.arnold.core.statemachine;

/**
 * A trigger fed into the state machine. The {@code type} (an enum) selects the transition to run,
 * while {@code payload} carries event-specific data the action needs (e.g. the collated persona
 * results behind {@code ANALYSIS_COMPLETED}, or provider merge info behind {@code CHANGE_MERGED}).
 */
public record TransitionEvent<E extends Enum<E>, P>(
        E type,
        P payload
) {
    public static <E extends Enum<E>, P> TransitionEvent<E, P> of(final E type, final P payload) {
        return new TransitionEvent<>(type, payload);
    }

    public static <E extends Enum<E>, P> TransitionEvent<E, P> of(final E type) {
        return new TransitionEvent<>(type, null);
    }
}
