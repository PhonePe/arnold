package com.phonepe.devx.arnold.models.mergerequest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.phonepe.devx.arnold.models.analysis.TokenUsage;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;
import java.util.List;

@Value
@Builder
@Jacksonized
public class SmartDiffResultRequest {
    @NotBlank
    @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
    String executionId;
    @NotBlank
    @Pattern(regexp = "[0-9a-fA-F]{40,64}")
    String headSha;
    @NotBlank
    @Pattern(regexp = "running|completed|failed")
    String status;
    @Valid
    @NotNull
    @Size(max = 100)
    List<@NotNull SmartDiffGroup> groups;
    @Size(max = 10000)
    String summary;
    @Size(max = 4000)
    String errorMessage;
    @Pattern(regexp = "queued|running|completed|failed")
    String commentAssessmentStatus;
    @PositiveOrZero
    Integer assessedCommentCount;
    @Valid
    TokenUsage usage;

    @JsonIgnore
    @AssertTrue
    public boolean isLifecycleValid() {
        if ("completed".equals(commentAssessmentStatus) && assessedCommentCount == null) {
            return false;
        }
        if ("failed".equals(status)) {
            return groups != null && groups.isEmpty() && summary == null
                    && errorMessage != null && !errorMessage.isBlank();
        }
        if ("running".equals(status)) {
            return groups != null && groups.isEmpty() && summary == null && errorMessage == null;
        }
        return !"completed".equals(status) || errorMessage == null && summary != null && !summary.isBlank()
                && groups != null && !groups.isEmpty();
    }
}
