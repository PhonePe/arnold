package com.phonepe.devx.arnold.core.model.review;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livetheoogway.crudstore.core.Id;

import java.util.List;
import java.util.Map;

public record ReviewProgress(String id, String headSha, List<String> filePaths, List<String> storyStepIds,
                             Map<String, String> focusAreaFeedback, long updatedAt) implements Id {
    @JsonCreator
    public ReviewProgress(@JsonProperty("id") final String id,
                          @JsonProperty("headSha") final String headSha,
                          @JsonProperty("filePaths") final List<String> filePaths,
                          @JsonProperty("storyStepIds") final List<String> storyStepIds,
                          @JsonProperty("focusAreaFeedback") final Map<String, String> focusAreaFeedback,
                          @JsonProperty("updatedAt") final long updatedAt) {
        this.id = id;
        this.headSha = headSha;
        this.filePaths = filePaths == null ? List.of() : List.copyOf(filePaths);
        this.storyStepIds = storyStepIds == null ? List.of() : List.copyOf(storyStepIds);
        this.focusAreaFeedback = focusAreaFeedback == null ? Map.of() : Map.copyOf(focusAreaFeedback);
        this.updatedAt = updatedAt;
    }

    public static String key(final String userId, final long projectId, final long iid, final String headSha) {
        return "progress:" + userId + ":gitlab:" + projectId + ":" + iid + ":" + headSha;
    }
}
