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

package com.phonepe.arnold.core.statemachine;

/**
 * Default {@link ActionResolver} that instantiates action/guard classes reflectively via their no-arg
 * constructor. Suitable for tests and for stateless actions; production wiring uses a Guice-backed
 * resolver so actions can be injected singletons.
 */
public enum ReflectiveActionResolver implements ActionResolver {
    INSTANCE;

    @Override
    public <T> T resolve(final Class<T> type) {
        try {
            final var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Unable to instantiate %s reflectively; provide a custom ActionResolver".formatted(type.getName()), e);
        }
    }
}
