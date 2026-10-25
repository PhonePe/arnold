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

package com.phonepe.arnold.core.model.analysis;

import com.livetheoogway.crudstore.core.Id;
import com.phonepe.arnold.models.analysis.GroupAnalysisResponse;
import lombok.With;

import java.util.List;
import java.util.Map;

@With
public record GroupAnalysisRun(String id, String invokedBy, GroupAnalysisResponse.StartRequest context,
                               List<GroupAnalysisResponse.Persona> reviewPersonas,
                               GroupAnalysisResponse result, String workerId, long sequence,
                               boolean publicationOnly, long executionStartedAt,
                               Map<String, GroupAnalysisResponse.MemberReview> memberReviews,
                               boolean analysisComplete) implements Id {
    public GroupAnalysisRun {
        memberReviews = memberReviews == null ? Map.of() : Map.copyOf(memberReviews);
    }
}
