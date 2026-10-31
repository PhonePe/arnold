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

package com.phonepe.arnold.core.model.vcs;

public record ChangeRequestSync(ChangeRequestRecord changeRequest, Action action, String actor) {
    /**
     * @return the lifecycle action implied by moving from {@code previous} (null when unknown) to {@code current}
     */
    public static Action actionFor(final ChangeRequestRecord previous, final ChangeRequestRecord current) {
        if ("merged".equalsIgnoreCase(current.state())) {
            return Action.MERGED;
        }
        if ("closed".equalsIgnoreCase(current.state())) {
            return Action.CLOSED;
        }
        if (previous != null && "closed".equalsIgnoreCase(previous.state()) && current.isOpen()) {
            return Action.REOPENED;
        }
        return Action.UPSERT;
    }

    public enum Action {
        UPSERT,
        MERGED,
        CLOSED,
        REOPENED
    }
}
