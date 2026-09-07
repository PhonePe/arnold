package com.phonepe.devx.arnold.core.models.feedback;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.phonepe.devx.arnold.models.request.TargetScope;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class TriggerRuleGenRequest {

    /**
     * Full path in the skills repo hierarchy.
     * Examples: "devx" (group), "devx/payments" (subgroup), "devx/payments/checkout-api" (project).
     */
    @NotBlank
    private String targetPath;

    /**
     * Scope level — determines how feedback is queried and where rules are written.
     */
    @Builder.Default
    private TargetScope scope = TargetScope.GROUP;

    @Min(1)
    @Max(90)
    @Builder.Default
    private int daysBack = 30;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Builder.Default
    private double minScore = 0.5;

    @AssertTrue(message = "PROJECT scope requires a fully-qualified path (namespace/projectName)")
    public boolean isProjectTargetPathValid() {
        if (scope != TargetScope.PROJECT) {
            return true;
        }
        final int lastSlash = targetPath == null ? -1 : targetPath.lastIndexOf('/');
        return lastSlash > 0 && lastSlash < targetPath.length() - 1;
    }
}
