/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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

package com.phonepe.arnold.core.services.copilot;

public class CopilotClientFailure extends RuntimeException {
    private final int status;
    private final String code;

    public CopilotClientFailure(final int status, final String code) {
        super("GitHub authentication request failed");
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
}
