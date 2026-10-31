package com.phonepe.devx.arnold.core.config;

import lombok.Data;

@Data
public class OpenCodeConfiguration {
    private boolean enabled;
    private String godricNamespace = "global";
    private String defaultModel;
}
