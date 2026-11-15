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

package com.phonepe.arnold.core.statemachine.store;

import com.phonepe.arnold.core.statemachine.StateInfo;

/**
 * A {@link StateInfo} paired with the opaque concurrency {@code version} it was read at. The version is
 * a token whose only guarantee is that it changes whenever the stored state mutates; callers must treat
 * it as opaque (read it via {@link StateStore#get}, pass it back to {@link StateStore#checkAndSet})
 * without interpreting it. This mirrors crud-store's {@code Versioned}/{@code ConcurrentStore} model so
 * a durable backend can supply its natural token (Aerospike record generation, an ETag, etc.).
 */
public record VersionedState<S extends Enum<S>, D>(
        StateInfo<S, D> state,
        Object version
) {
}
