package com.phonepe.devx.arnold.models.mergerequest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

@Value
@Builder
@Jacksonized
public class SmartDiffChange {
    @NotBlank
    @Size(max = 1024)
    String filePath;
    @PositiveOrZero
    @JsonInclude(JsonInclude.Include.ALWAYS)
    Integer oldStartLine;
    @PositiveOrZero
    @JsonInclude(JsonInclude.Include.ALWAYS)
    Integer oldEndLine;
    @PositiveOrZero
    @JsonInclude(JsonInclude.Include.ALWAYS)
    Integer newStartLine;
    @PositiveOrZero
    @JsonInclude(JsonInclude.Include.ALWAYS)
    Integer newEndLine;

    @JsonIgnore
    @AssertTrue
    public boolean isSelectorValid() {
        return completeRange(oldStartLine, oldEndLine) && completeRange(newStartLine, newEndLine)
                && (oldStartLine != null || newStartLine != null);
    }

    private boolean completeRange(final Integer start, final Integer end) {
        return start == null && end == null || start != null && end != null && end >= start;
    }
}
