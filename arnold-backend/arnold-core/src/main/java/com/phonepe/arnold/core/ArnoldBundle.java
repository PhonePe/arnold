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

package com.phonepe.arnold.core;

import com.google.inject.AbstractModule;
import com.phonepe.arnold.core.guice.BundleModule;
import com.phonepe.arnold.core.guice.ConfigModule;
import com.phonepe.arnold.core.guice.CoreModule;
import com.phonepe.arnold.core.guice.DBModule;
import com.phonepe.arnold.core.utils.MapperUtils;
import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class ArnoldBundle<T extends Configuration> implements ConfiguredBundle<T> {

    @Override
    public void run(T configuration, Environment environment) {
        MapperUtils.configureMapper(environment.getObjectMapper());
    }

    @Override
    public void initialize(Bootstrap bootstrap) {

    }

    public Stream<AbstractModule> modules(Bootstrap<T> bootstrap) {
        return Stream.of(
                new BundleModule(
                        bootstrap.getObjectMapper(),
                        bootstrap.getMetricRegistry(),
                        bundleConfigSupplier()),
                new DBModule(),
                new CoreModule(),
                new ConfigModule()
        );
    }

    public Stream<String> packageNameList() {
        return Stream.of("com.phonepe.arnold.core");
    }

    public abstract Supplier<ArnoldCoreConfiguration> bundleConfigSupplier();

}
