package com.phonepe.devx.arnold.models.analysis;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

public record AnalysisFindingResponse(
        @NotBlank @Size(max = 128) String id,
        @NotBlank String headSha,
        @Size(max = 1024) String filePath,
        @Size(max = 1024) String oldPath,
        @Positive Integer newLine,
        @NotBlank @Size(max = 20000) String body,
        @NotBlank @Pattern(regexp = "posted|failed|unknown") String publicationStatus,
        @Size(max = 2048) String commentUrl,
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,127}") String personaId,
        @PositiveOrZero Long personaVersion
) {
    /**
     * A finding from the general Improve pass, not attributed to any review persona.
     */
    public AnalysisFindingResponse(final String id, final String headSha, final String filePath, final String oldPath,
                                   final Integer newLine, final String body, final String publicationStatus,
                                   final String commentUrl) {
        this(id, headSha, filePath, oldPath, newLine, body, publicationStatus, commentUrl, null, null);
    }
}
