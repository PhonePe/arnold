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
