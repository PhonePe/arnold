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

import java.util.Map;

/**
 * Starts a runner job in a specific execution environment.
 *
 * <p>A job is described entirely by string variables (for example {@code WORKFLOW}, {@code ARNOLD_RUN_ID}); every
 * mode hands the same variables to {@code arnold-runner} as environment variables.</p>
 */
public interface RunnerBackend {

    RunnerMode mode();

    /**
     * @return an identifier for the started job (pipeline id, workflow run id or queued job number)
     */
    long submit(Map<String, String> variables);

    /**
     * @return a web URL for the job, or null when the mode has none
     */
    String jobUrl(long jobId);
}
