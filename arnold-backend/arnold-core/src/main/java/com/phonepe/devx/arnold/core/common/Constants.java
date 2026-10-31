package com.phonepe.devx.arnold.core.common;

import com.google.common.base.Strings;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Constants {

    public static final String HOSTNAME = hostname();
    public static final String OLYMPUS = "Olympus";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String OLYMPUS_TOKEN ="Olympus token";

    public static final String INDEX_REFERENCE = "CORE_REVIEWS";

    public static final String FIELD_NOTE_CREATED_AT = "noteCreatedAt";
    public static final String FIELD_PROJECT_NAME = "project.projectName";
    public static final String FIELD_MR_AUTHOR = "mrAuthor";
    public static final String SORT_FIELD = "ingestedAt";

    public static final String STATE_OPENED = "opened";
    public static final String STATE_MERGED = "merged";
    public static final String STATE_CLOSED = "closed";
    public static final String STATE_PROCESSED = "processed";
    public static final String STATE_PROCESSING = "processing";
    public static final String GROUP = "GROUP";
    public static final String MERGE_REQUEST = "MERGE_REQUEST";

    /* Stats related constants */
    public static final String COUNTS_AGGREGATION = "counts";
    public static final String DATA_THROUGH_AGGREGATION = "data_through";
    public static final String OPEN_AUTHORED = "open_authored";
    public static final String OPEN_REVIEW_WORK = "open_review_work";
    public static final String CURRENT_THROUGHPUT = "current_throughput";
    public static final String PREVIOUS_THROUGHPUT = "previous_throughput";
    public static final String REVIEWER_IDS = "reviewerIds";
    public static final String AUTHOR_ID = "authorId";
    public static final String AUTHORED = "authored";
    public static final String MERGED_AT = "mergedAt";
    public static final String CLOSED_AT = "closedAt";
    public static final String REVIEWED = "reviewed";
    public static final String ASSIGNEE_IDS = "assigneeIds";

    private String hostname() {
        final String host = System.getenv("HOST");
        return Strings.isNullOrEmpty(host) ? "UNKNOWN" : host;
    }
}
