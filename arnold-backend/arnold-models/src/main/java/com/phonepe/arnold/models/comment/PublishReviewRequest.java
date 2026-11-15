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

package com.phonepe.arnold.models.comment;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Pattern;

@Value
@Builder
@Jacksonized
public class PublishReviewRequest {
    @Builder.Default
    CreateDiscussionRequest.VersionControlProvider provider = CreateDiscussionRequest.VersionControlProvider.GITLAB;
    @Positive
    long projectId;
    @Positive
    long mrIid;
    String summary;
    @NotBlank
    @Pattern(regexp = "reviewed|requested_changes|approved")
    String reviewerState;
}
