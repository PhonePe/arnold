package com.phonepe.devx.arnold.core.models.feedback;

import com.phonepe.devx.arnold.models.request.AcceptanceStatus;
import com.phonepe.devx.arnold.models.request.Priority;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class FeedbackQueryParams {
    List<AcceptanceStatus> statuses;
    TargetScope scope;
    String targetPath;
    String teamName;
    int daysBack;
    double minScore;
    Priority minPriority;
    int limit;
}
