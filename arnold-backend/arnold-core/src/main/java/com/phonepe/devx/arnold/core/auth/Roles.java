package com.phonepe.devx.arnold.core.auth;

import lombok.experimental.UtilityClass;

/**
 * Role names referenced by {@code @RolesAllowed} on Arnold resources.
 */
@UtilityClass
public class Roles {
    public static final String SUPERADMIN = "superadmin";
    public static final String ANALYSIS_CALLBACK_WRITE = "analysis_callback_write";
    public static final String SMART_DIFF_CALLBACK_WRITE = "smart_diff_callback_write";
    public static final String REVIEW_FEEDBACK_READ = "review_feedback_read";
    public static final String REVIEW_FEEDBACK_WRITE = "review_feedback_write";
    public static final String BACKFILL_REVIEW_FEEDBACK = "backfill_review_feedback";
}
