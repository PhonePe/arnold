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

package com.phonepe.arnold.core.model.review;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Lifecycle states of an Arnold change-request review. Enhanced over raw provider states to model
 * Arnold's analysis phases.
 *
 * <p>{@code MERGED} is the only terminal state. {@code CLOSED} is
 * <i>not</i> terminal because providers can reopen a closed change request, so the machine can leave
 * {@code CLOSED} via {@code CHANGE_REOPENED}.
 *
 * <p>Analysis failure is intentionally <b>not</b> a state here: a failed analysis still resolves to
 * {@code ANALYSED}, carrying the failure outcome in {@link ReviewStateData}. Retrying/alerting on a
 * failed run is handled outside the state machine.
 */
public enum ReviewState {
    CREATED,
    ANALYSING,
    ANALYSED,
    @JsonAlias("READY_FOR_MERGE")
    REVIEW_READY,
    MERGED,
    CLOSED
}
