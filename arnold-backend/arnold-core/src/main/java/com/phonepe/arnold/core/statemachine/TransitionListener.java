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
 * Notified after a transition has been committed to the {@link com.phonepe.arnold.core.statemachine.store.StateStore}.
 * Listeners run side effects that must not influence the transition decision itself — ES indexing,
 * metrics, UI/notification fan-out. Listener failures are logged and swallowed by the engine so they
 * never roll back a committed state change.
 *
 * <p>This is the natural seam for an {@code io.appform.signals} broadcast bus if a fan-out of
 * listeners is desired.
 */
@FunctionalInterface
public interface TransitionListener<S extends Enum<S>, E extends Enum<E>, D, P> {

    void onTransition(String entityId,
                      StateInfo<S, D> from,
                      StateInfo<S, D> to,
                      TransitionEvent<E, P> event);
}
