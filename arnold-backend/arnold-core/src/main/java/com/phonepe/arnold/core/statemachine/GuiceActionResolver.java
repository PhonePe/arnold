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

package com.phonepe.arnold.core.statemachine;

import com.google.inject.Injector;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * {@link ActionResolver} backed by the Guice {@link Injector}, so transition actions and guards can be
 * dependency-injected singletons (e.g. {@code InvokeAnalysisAction} reaching the
 * {@code AnalysisOrchestrator}). This is the production resolver; tests use the reflective default.
 */
@Singleton
public class GuiceActionResolver implements ActionResolver {

    private final Injector injector;

    @Inject
    public GuiceActionResolver(final Injector injector) {
        this.injector = injector;
    }

    @Override
    public <T> T resolve(final Class<T> type) {
        return injector.getInstance(type);
    }
}
