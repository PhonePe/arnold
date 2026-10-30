package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.models.es.ReviewFeedbackDocument;
import com.phonepe.devx.arnold.models.request.FeedbackQueryRequest;
import com.phonepe.devx.arnold.models.request.ReviewFeedbackRequest;

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
