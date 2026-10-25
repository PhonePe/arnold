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

package com.phonepe.arnold.core.model.review;

import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;

import java.util.List;
import java.util.Map;

public record ReviewProgress(String id, String headSha, List<String> filePaths, List<String> storyStepIds,
                             Map<String, String> focusAreaFeedback, long updatedAt) implements Id {
    @JsonCreator
    public ReviewProgress(@JsonProperty("id") final String id,
                          @JsonProperty("headSha") final String headSha,
                          @JsonProperty("filePaths") final List<String> filePaths,
                          @JsonProperty("storyStepIds") final List<String> storyStepIds,
                          @JsonProperty("focusAreaFeedback") final Map<String, String> focusAreaFeedback,
                          @JsonProperty("updatedAt") final long updatedAt) {
        this.id = id;
        this.headSha = headSha;
        this.filePaths = filePaths == null ? List.of() : List.copyOf(filePaths);
        this.storyStepIds = storyStepIds == null ? List.of() : List.copyOf(storyStepIds);
        this.focusAreaFeedback = focusAreaFeedback == null ? Map.of() : Map.copyOf(focusAreaFeedback);
        this.updatedAt = updatedAt;
    }

    public static String key(final VersionControlProviderType provider, final String userId, final long projectId,
                             final long iid, final String headSha) {
        return "progress:" + userId + ":" + provider.name().toLowerCase(java.util.Locale.ROOT) + ":" + projectId
                + ":" + iid + ":" + headSha;
    }
}
