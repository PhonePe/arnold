package com.phonepe.devx.arnold.core.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Client interface for communicating with the Godric LLM gateway to fetch
 * onboarded models for the OpenCode harness.
 */
public interface GodricService {

    /**
     * Lists active LLM entities onboarded in Godric for the configured namespace.
     * Calls: GET /v1/entity/llm/{namespace}?skipInactive=true
     */
    List<GodricLLMEntity> listLLMs();

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GodricLLMEntity(String id, String name, String description, boolean active, Lifecycle lifecycle) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Lifecycle(String state) { }
    }
}
