package com.phonepe.devx.arnold.core.models.feedback;

import com.phonepe.devx.arnold.models.request.AcceptanceStatus;
import com.phonepe.devx.arnold.models.request.Priority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackQueryParams {

    @QueryParam("status")
    private List<AcceptanceStatus> statuses;

    @QueryParam("scope")
    @DefaultValue("GROUP")
    @Builder.Default
    private TargetScope scope = TargetScope.GROUP;

    @QueryParam("targetPath")
    private String targetPath;

    @QueryParam("teamName")
    private String teamName;

    @QueryParam("daysBack")
    @DefaultValue("30")
    @Min(1)
    @Max(90)
    @Builder.Default
    private Integer daysBack = 30;

    @QueryParam("minScore")
    @DefaultValue("0.5")
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Builder.Default
    private Double minScore = 0.5D;

    @QueryParam("minPriority")
    private Priority minPriority;

    @QueryParam("limit")
    @DefaultValue("500")
    @Min(1)
    @Max(1000)
    @Builder.Default
    private Integer limit = 500;

    @QueryParam("offset")
    @DefaultValue("0")
    @Min(0)
    @Builder.Default
    private Integer offset = 0;

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
