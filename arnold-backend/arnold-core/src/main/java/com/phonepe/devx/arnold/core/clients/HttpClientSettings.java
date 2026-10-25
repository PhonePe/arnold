package com.phonepe.devx.arnold.core.clients;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;

/**
 * Connection settings for an outbound HTTP dependency.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpClientSettings {
    private String url;
    @Min(1)
    @Builder.Default
    private int connectTimeoutMs = 10_000;
    @Min(1)
    @Builder.Default
    private int opTimeoutMs = 30_000;
    @Min(1)
    @Builder.Default
    private int connections = 10;
    @Min(1)
    @Builder.Default
    private int idleTimeOutSeconds = 30;
}
