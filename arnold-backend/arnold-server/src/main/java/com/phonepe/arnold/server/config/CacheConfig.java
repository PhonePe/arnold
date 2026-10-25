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

package com.phonepe.arnold.server.config;

import javax.validation.constraints.Max;

public record CacheConfig(@Max(100_000) int maxCacheSize,
                          @Max(86400 * 30) long expireAfterWriteInSeconds, // max of 30days
                          @Max(86400 * 30) long refreshAfterWriteInSeconds) { // max of 30 days
}
