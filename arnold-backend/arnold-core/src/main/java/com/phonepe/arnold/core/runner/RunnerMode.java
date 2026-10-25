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

package com.phonepe.arnold.core.runner;

/**
 * Where review workflows execute.
 */
public enum RunnerMode {
    /** Jobs are queued in Arnold and claimed by self-hosted {@code arnold-runner worker} processes. */
    LOCAL,
    /** Jobs run as GitLab CI pipelines triggered through the pipeline trigger API. */
    GITLAB,
    /** Jobs run as GitHub Actions workflows started through {@code workflow_dispatch}. */
    GITHUB
}
