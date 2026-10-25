package com.phonepe.devx.arnold.models.es;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * Outcome of a review-feedback backfill run.
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class BackfillResult {

    /** Whether this was a dry run (no writes). */
    boolean dryRun;

    /** Number of documents read from the worklist. */
    int scanned;

    /** Number of documents successfully re-enriched and upserted (0 on dry run). */
    int enriched;

    /** Number of documents that were re-enrichable in a dry run (what would be written). */
    int enrichable;

    /**
     * Documents skipped because SCM metadata still returned no data for the missing fields
     * (deleted/unavailable note/project/user) - captured for manual review.
     */
    List<String> unEnrichableIds;

    /** Documents that failed with an unexpected error and should be retried by re-running. */
    List<String> failedIds;

    /**
     * Whether the worklist was fully drained (all remaining records were either enriched, or are
     * un-enrichable / failed). When false, the run stopped early due to maxPages - re-run to continue.
     */
    boolean drained;
}
