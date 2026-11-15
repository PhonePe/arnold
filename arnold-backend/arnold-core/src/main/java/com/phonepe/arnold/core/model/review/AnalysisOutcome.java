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

/**
 * Result quality of an analysis run, as determined by the orchestrator after the persona fan-in. The
 * state machine treats all three as a successful arrival at {@code ANALYSED}; the distinction drives
 * UI presentation and out-of-band retry/alerting, not the transition itself.
 */
public enum AnalysisOutcome {
    /** Every persona review completed successfully. */
    SUCCEEDED,
    /** Some personas completed, others failed; partial findings are available. */
    PARTIAL,
    /** No usable findings were produced (e.g. runner unavailable, all personas errored/timed out). */
    FAILED
}
