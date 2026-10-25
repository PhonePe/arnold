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

package com.phonepe.arnold.core.config;

import lombok.Data;
import lombok.ToString;

import java.util.List;

/**
 * Settings for the OpenCode harness.
 * Models are either listed statically in {@code models}, or discovered from an OpenAI-compatible
 * {@code GET {modelsUrl}/models} endpoint. Model ids are exposed to runners as {@code provider/id}.
 */
@Data
public class OpenCodeConfiguration {
    private boolean enabled;
    private String provider = "openai";
    private String defaultModel;
    private String modelsUrl;
    @ToString.Exclude
    private String apiKey;
    private List<ModelEntry> models = List.of();

    @Data
    public static class ModelEntry {
        private String id;
        private String name;
    }
}
