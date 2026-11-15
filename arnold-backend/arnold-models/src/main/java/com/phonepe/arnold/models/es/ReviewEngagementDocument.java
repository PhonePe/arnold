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

package com.phonepe.arnold.models.es;

import java.util.List;

/**
 * Per-reviewer, per-head-SHA summary of how a reviewer used the Arnold review workspace.
 */
public record ReviewEngagementDocument(
        String id,
        String userId,
        String mergeRequestId,
        String provider,
        String repositoryId,
        long number,
        String headSha,
        int filesViewed,
        int storyStepsCompleted,
        int focusAreasRated,
        int focusAreasUseful,
        int focusAreasNotUseful,
        List<String> usefulAreas,
        List<String> notUsefulAreas,
        long updatedAt
) {
}
