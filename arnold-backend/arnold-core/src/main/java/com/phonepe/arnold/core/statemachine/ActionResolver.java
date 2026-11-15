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

/**
 * Resolves the action/guard classes declared on a {@link Transition} into live instances. The default
 * production binding delegates to the Guice {@code Injector}, so actions can be DI-managed singletons
 * that reach collaborators (orchestrator, services). Tests can supply a trivial reflective resolver.
 */
public interface ActionResolver {

    <T> T resolve(Class<T> type);
}
