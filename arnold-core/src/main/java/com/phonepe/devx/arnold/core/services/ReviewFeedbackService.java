package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.request.ReviewFeedbackRequest;

public interface ReviewFeedbackService {

    /**
     * Accepts review feedback from the impact analysis pipeline and persists
     * each comment as a separate document in Elasticsearch for Kibana analytics.
     *
     * @return number of comments ingested
     */
    int ingestFeedback(ReviewFeedbackRequest request);
}
