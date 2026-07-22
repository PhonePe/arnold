package com.phonepe.devx.arnold.server.config;

import javax.validation.constraints.Max;

public record CacheConfig(@Max(100_000) int maxCacheSize,
                          @Max(86400 * 30) long expireAfterWriteInSeconds, // max of 30days
                          @Max(86400 * 30) long refreshAfterWriteInSeconds) { // max of 30 days
}
