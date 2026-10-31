package com.phonepe.devx.arnold.core.model.analysis;

import com.livetheoogway.crudstore.core.Id;
import com.phonepe.devx.arnold.models.analysis.GroupAnalysisResponse;
import com.phonepe.devx.arnold.models.analysis.TokenUsage;
import lombok.With;

import java.util.List;
import java.util.Map;

@With
public record GroupAnalysisRun(String id, String invokedBy, GroupAnalysisResponse.StartRequest context,
                               List<GroupAnalysisResponse.Persona> reviewPersonas,
                               GroupAnalysisResponse result, String workerId, long sequence,
                               boolean publicationOnly, long executionStartedAt,
                               Map<String, GroupAnalysisResponse.MemberReview> memberReviews,
                               boolean analysisComplete,
                               Map<String, TokenUsage> usageByExecution) implements Id {
    public GroupAnalysisRun {
        memberReviews = memberReviews == null ? Map.of() : Map.copyOf(memberReviews);
        usageByExecution = usageByExecution == null ? Map.of() : Map.copyOf(usageByExecution);
    }
}
