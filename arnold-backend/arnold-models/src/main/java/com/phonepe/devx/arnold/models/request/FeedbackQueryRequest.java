package com.phonepe.devx.arnold.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeedbackQueryRequest {

    List<AcceptanceStatus> statuses;

    @Builder.Default
    TargetScope scope = TargetScope.GROUP;

    String targetPath;

    String teamName;

    @NotNull
    @Min(1)
    @Max(90)
    @Builder.Default
    Integer daysBack = 30;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Builder.Default
    Double minScore = 0.5D;

    Priority minPriority;

    @NotNull
    @Min(1)
    @Max(1000)
    @Builder.Default
    Integer limit = 500;

    @NotNull
    @Min(0)
    @Builder.Default
    Integer offset = 0;

    @AssertTrue(message = "PROJECT scope requires a fully-qualified path (namespace/projectName)")
    public boolean isProjectTargetPathValid() {
        if (scope != TargetScope.PROJECT) {
            return true;
        }
        if (targetPath == null || targetPath.isBlank()) {
            return false;
        }
        final int lastSlash = targetPath.lastIndexOf('/');
        return lastSlash > 0 && lastSlash < targetPath.length() - 1;
    }
}
