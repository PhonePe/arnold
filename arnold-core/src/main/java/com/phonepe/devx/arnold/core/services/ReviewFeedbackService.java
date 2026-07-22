package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.models.feedback.FeedbackQueryParams;
import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.devx.arnold.models.request.ReviewFeedbackRequest;

import java.util.List;

public interface ReviewFeedbackService {

    /**
     * Accepts review feedback from the impact analysis pipeline and persists
     * each comment as a separate document in Elasticsearch for Kibana analytics.
     *
     * @return number of comments ingested
     */
    int ingestFeedback(ReviewFeedbackRequest request);

    /**
     * Query accepted feedback with optional status, scope and priority filters.
     * Defaults to APPLIED and PARTIALLY_APPLIED when no statuses are specified.
     */
    List<ReviewFeedbackDocument> getFeedback(FeedbackQueryParams params);
}
