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
 * Thrown when an action returns a target state that is not among the transition's declared {@code to}
 * states. Indicates a bug in the action or transition table rather than a caller error.
 */
public class InvalidTargetStateException extends RuntimeException {

    public <S extends Enum<S>> InvalidTargetStateException(final String entityId,
                                                           final S resolved,
                                                           final Object allowed) {
        super("Action for entity=%s resolved to state=%s which is not in allowed targets=%s"
                .formatted(entityId, resolved, allowed));
    }
}
