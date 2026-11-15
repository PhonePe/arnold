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

package com.phonepe.arnold.server.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.phonepe.columbus.Columbus;
import com.phonepe.columbus.ColumbusBundle;
import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.server.ArnoldConfiguration;
import com.phonepe.arnold.server.config.ElasticSearchConfiguration;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ServerModule extends AbstractModule {

    private final ColumbusBundle<ArnoldConfiguration> columbusBundle;

    @Provides
    @Singleton
    public ArnoldCoreConfiguration getServiceCoreConfiguration(final ArnoldConfiguration configuration) {
        return configuration.getServiceCoreConfiguration();
    }

    @Provides
    @Singleton
    public ElasticSearchConfiguration elasticSearchConfiguration(final ArnoldConfiguration configuration) {
        return configuration.getElasticSearchConfiguration();
    }

    @Provides
    @Singleton
    public Columbus getColumbus() {
        return columbusBundle.columbus();
    }
}
