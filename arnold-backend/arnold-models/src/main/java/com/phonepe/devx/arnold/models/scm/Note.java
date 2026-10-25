package com.phonepe.devx.arnold.models.scm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A review comment (note) on a change request, as returned by the SCM provider.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Note {
    private Integer id;

    private String body;

    @JsonProperty("created_at")
    private String createdAt;

    private User author;

    private NotePosition position;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NotePosition {
        @JsonProperty("new_path")
        private String newPath;

        @JsonProperty("old_path")
        private String oldPath;

        @JsonProperty("new_line")
        private Integer newLine;

        @JsonProperty("old_line")
        private Integer oldLine;
    }
}
