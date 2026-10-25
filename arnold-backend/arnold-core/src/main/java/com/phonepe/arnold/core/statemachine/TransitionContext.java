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
 * Everything an action or guard needs to evaluate a single transition: the {@code entityId} being
 * transitioned (e.g. the MR id), the {@code from} state snapshot it is leaving, and the {@code event}
 * that triggered the transition. This is read-only; an action communicates its outcome purely through
 * the {@link StateInfo} it returns.
 */
public record TransitionContext<S extends Enum<S>, E extends Enum<E>, D, P>(
        String entityId,
        StateInfo<S, D> from,
        TransitionEvent<E, P> event
) {
    public S fromState() {
        return from.state();
    }

    public D fromData() {
        return from.data();
    }

    public E eventType() {
        return event.type();
    }

    public P payload() {
        return event.payload();
    }
}
