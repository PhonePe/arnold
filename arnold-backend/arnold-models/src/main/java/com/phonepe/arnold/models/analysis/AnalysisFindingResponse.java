package com.phonepe.arnold.models.analysis;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

public record AnalysisFindingResponse(
        @NotBlank @Size(max = 128) String id,
        @NotBlank String headSha,
        @Size(max = 1024) String filePath,
        @Size(max = 1024) String oldPath,
        @Positive Integer newLine,
        @NotBlank @Size(max = 20000) String body,
        @NotBlank @Pattern(regexp = "posted|failed|unknown") String publicationStatus,
        @Size(max = 2048) String commentUrl
) {
}
