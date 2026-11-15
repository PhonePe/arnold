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

package com.phonepe.arnold.core.auth;

import lombok.experimental.UtilityClass;

/**
 * Role names referenced by {@code @RolesAllowed} on Arnold resources.
 */
@UtilityClass
public class Roles {
    public static final String SUPERADMIN = "superadmin";
    public static final String ANALYSIS_CALLBACK_WRITE = "analysis_callback_write";
    public static final String REVIEW_FEEDBACK_READ = "review_feedback_read";
    public static final String REVIEW_FEEDBACK_WRITE = "review_feedback_write";
    public static final String BACKFILL_REVIEW_FEEDBACK = "backfill_review_feedback";
    public static final String RUNNER_EXECUTE = "runner_execute";
}
