package com.phonepe.arnold.server.config;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

@Data
@Builder
@Jacksonized
public class ElasticSearchIndexConfiguration {
    @Builder.Default
    private int numberOfShards = 1;
    @Builder.Default
    private int numberOfReplicas = 1;
    private Map<String, Object> settings;
}
