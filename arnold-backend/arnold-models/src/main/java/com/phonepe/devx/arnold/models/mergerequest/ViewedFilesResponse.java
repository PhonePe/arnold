package com.phonepe.devx.arnold.models.mergerequest;

import java.util.List;
import java.util.Map;

public record ViewedFilesResponse(String headSha, List<String> filePaths, List<String> storyStepIds,
                                  Map<String, String> focusAreaFeedback, long updatedAt) {
    public ViewedFilesResponse {
        filePaths = filePaths == null ? List.of() : List.copyOf(filePaths);
        storyStepIds = storyStepIds == null ? List.of() : List.copyOf(storyStepIds);
        focusAreaFeedback = focusAreaFeedback == null ? Map.of() : Map.copyOf(focusAreaFeedback);
    }
}
