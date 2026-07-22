package com.phonepe.devx.arnold.core.statemachine;

/**
 * Resolves the action/guard classes declared on a {@link Transition} into live instances. The default
 * production binding delegates to the Guice {@code Injector}, so actions can be DI-managed singletons
 * that reach collaborators (orchestrator, services). Tests can supply a trivial reflective resolver.
 */
public interface ActionResolver {

    <T> T resolve(Class<T> type);
}
