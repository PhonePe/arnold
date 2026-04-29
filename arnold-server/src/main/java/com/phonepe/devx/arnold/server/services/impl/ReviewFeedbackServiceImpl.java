package com.phonepe.devx.arnold.server.services.impl;

import com.google.inject.Singleton;
import com.phonepe.devx.arnold.core.services.ReviewFeedbackService;
import com.phonepe.devx.arnold.models.request.ReviewFeedbackRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@SuppressWarnings("java:S1135") // TODO: persistence will be implemented in a follow-up PR
public class ReviewFeedbackServiceImpl implements ReviewFeedbackService {

    @Override
    public int ingestFeedback(ReviewFeedbackRequest request) {
        // TODO: implement persistence
        log.info("Received {} review feedback comments for MR {} in project {}",
                request.getComments().size(),
                request.getMetadata().getMergeRequestId(),
                request.getMetadata().getProjectId());
        return request.getComments().size();
    }
}
