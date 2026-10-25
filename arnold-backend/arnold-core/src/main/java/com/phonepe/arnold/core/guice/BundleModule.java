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

package com.phonepe.arnold.core.guice;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import lombok.AllArgsConstructor;

import java.util.function.Supplier;

@AllArgsConstructor
public class BundleModule extends AbstractModule {

    private final ObjectMapper objectMapper;
    private final MetricRegistry metricRegistry;
    private final Supplier<ArnoldCoreConfiguration> bundleConfigSupplier;

    @Provides
    @Singleton
    public MetricRegistry metricRegistry() {
        return metricRegistry;
    }

    @Provides
    @Singleton
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Provides
    @Singleton
    public Supplier<ArnoldCoreConfiguration> getArnoldBundleConfigSupplier() {
        return bundleConfigSupplier;
    }
}
