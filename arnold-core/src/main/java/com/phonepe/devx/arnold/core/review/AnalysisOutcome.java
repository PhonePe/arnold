package com.phonepe.devx.arnold.core.review;

/**
 * Result quality of an analysis run, as determined by the orchestrator after the persona fan-in. The
 * state machine treats all three as a successful arrival at {@code ANALYSED}; the distinction drives
 * UI presentation and out-of-band retry/alerting, not the transition itself.
 */
public enum AnalysisOutcome {
    /** Every persona review completed successfully. */
    SUCCEEDED,
    /** Some personas completed, others failed; partial findings are available. */
    PARTIAL,
    /** No usable findings were produced (e.g. CIBot unavailable, all personas errored/timed out). */
    FAILED
}
