package com.phonepe.arnold.models.comment;

import javax.validation.constraints.Positive;

public record DiscussionLineRange(
        String side,
        @Positive Long startOldLine,
        @Positive Long startNewLine,
        @Positive Long endOldLine,
        @Positive Long endNewLine
) {
}
