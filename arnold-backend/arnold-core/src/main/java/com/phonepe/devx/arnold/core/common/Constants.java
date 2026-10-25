package com.phonepe.devx.arnold.core.common;

import com.google.common.base.Strings;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Constants {

    public static final String HOSTNAME = hostname();
    public static final String BEARER_AUTH = "bearerAuth";
    public static final String BEARER_TOKEN = "Bearer token";

    public static final String INDEX_REFERENCE = "CORE_REVIEWS";

    public static final String FIELD_NOTE_CREATED_AT = "noteCreatedAt";
    public static final String FIELD_PROJECT_NAME = "project.projectName";
    public static final String FIELD_MR_AUTHOR = "mrAuthor";
    public static final String SORT_FIELD = "ingestedAt";

    private String hostname() {
        final String host = System.getenv("HOST");
        return Strings.isNullOrEmpty(host) ? "UNKNOWN" : host;
    }
}
