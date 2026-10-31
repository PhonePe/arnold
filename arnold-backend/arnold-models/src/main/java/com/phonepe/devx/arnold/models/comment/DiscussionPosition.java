package com.phonepe.devx.arnold.models.comment;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

public record DiscussionPosition(
        String baseSha,
        String startSha,
        String headSha,
        @NotBlank String oldPath,
        @NotBlank String newPath,
        @Positive Long oldLine,
        @Positive Long newLine,
        DiscussionLineRange lineRange
) {
}
