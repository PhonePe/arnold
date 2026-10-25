package com.phonepe.devx.arnold.core.config;

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
