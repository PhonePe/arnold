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
 * Composite lookup key for the transition table: a transition is uniquely identified by the state it
 * leaves and the event that triggers it. Two arrows out of the same state (e.g.
 * {@code ANALYSED --INVOKE_ANALYSIS--> ANALYSING} vs {@code ANALYSED --MARK_REVIEW_READY--> REVIEW_READY})
 * are distinguished purely by their event.
 */
public record TransitionKey<S extends Enum<S>, E extends Enum<E>>(
        S from,
        E on
) {
}
