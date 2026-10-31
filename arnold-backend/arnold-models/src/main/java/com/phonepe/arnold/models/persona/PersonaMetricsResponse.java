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

package com.phonepe.arnold.models.persona;

import java.util.List;

/**
 * Effectiveness of one review persona over {@code [from, to)}. Rates are percentages; null when there is nothing to
 * divide by. {@code acceptanceRate} = (applied + partially_applied) / (feedback - not_applicable).
 */
public record PersonaMetricsResponse(String personaId, long from, long to, long runs, Findings findings,
                                     Acceptance acceptance, Resolution resolution, List<Week> weekly,
                                     List<Version> versions) {

    public record Findings(long total, long posted, long failed) {
    }

    public record Acceptance(long feedback, long accepted, long actionable, Double acceptanceRate) {
    }

    public record Resolution(long comments, long resolvable, long resolved, Double resolutionRate) {
    }

    public record Week(String weekStart, long findings, long feedback, long accepted, Double acceptanceRate) {
    }

    public record Version(long version, long runs, long findings, long feedback, long accepted,
                          Double acceptanceRate) {
    }
}
