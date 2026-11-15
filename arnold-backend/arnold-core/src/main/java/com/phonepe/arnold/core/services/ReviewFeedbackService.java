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

package com.phonepe.arnold.core.services;

import com.phonepe.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.arnold.models.request.FeedbackQueryRequest;
import com.phonepe.arnold.models.request.ReviewFeedbackRequest;

import java.util.List;
import java.util.Map;

public interface ReviewFeedbackService {

    /**
     * Accepts review feedback from the impact analysis pipeline and persists
     * each comment as a separate document in Elasticsearch for Kibana analytics.
     *
     * @return number of comments ingested
     */
    int ingestFeedback(ReviewFeedbackRequest request);

    /**
     * Query review feedback with optional status, scope and priority filters.
     */
    List<ReviewFeedbackDocument> queryFeedback(FeedbackQueryRequest request);

    Map<Long, ReviewFeedbackDocument> feedbackByNote(long projectId, long mrIid, List<Long> noteIds);
}
