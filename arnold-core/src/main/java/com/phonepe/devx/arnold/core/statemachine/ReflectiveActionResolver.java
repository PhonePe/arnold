package com.phonepe.devx.arnold.core.statemachine;

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
