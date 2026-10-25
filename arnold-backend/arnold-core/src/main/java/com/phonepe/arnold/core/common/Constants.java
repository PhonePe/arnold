/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.common;

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
