package com.phonepe.devx.arnold.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class BackfillRequest {

    /**
     * When true, the run reads + resolves + counts but performs no writes. Use to validate the
     * worklist and confirm CIBOT availability before writing.
     */
    @Builder.Default
    boolean dryRun = true;

    /**
     * ES read page size. Kept at/under the fan-out executor queue capacity.
     */
    @Builder.Default
    @Min(1)
    @Max(500)
    int pageSize = 300;

    /**
     * Max in-flight records being re-enriched at once. Ceiling 20 (the getMergeRequestNote Hystrix
     * pool concurrency); default 10 to stay within the default pool too.
     */
    @Builder.Default
    @Min(1)
    @Max(20)
    int concurrency = 10;

    /**
     * Pause between pages in milliseconds, for rate smoothing during lean hours.
     */
    @Builder.Default
    @Min(0)
    long interPagePauseMs = 1000L;

    /**
     * Optional cap on number of pages to process in this invocation (0 = no cap, process until drained).
     */
    @Builder.Default
    @Min(0)
    private int maxPages = 0;
}