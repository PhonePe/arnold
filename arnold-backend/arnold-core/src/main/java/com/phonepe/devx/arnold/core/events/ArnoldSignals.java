package com.phonepe.devx.arnold.core.events;

import io.appform.signals.signals.ConsumingSyncSignal;

import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class ArnoldSignals implements ArnoldSignalPublisher, ArnoldSignalSubscriber {
    private final Map<Class<? extends ArnoldSignal>, ConsumingSyncSignal<? extends ArnoldSignal>> signals =
            new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ArnoldSignal> void publish(final T event) {
        signal((Class<T>) event.getClass()).dispatch(event);
    }

    @Override
    public <T extends ArnoldSignal> void subscribe(final Class<T> signalType,
                                                   final String name,
                                                   final io.appform.signals.signalhandlers.SignalConsumer<T> consumer) {
        signal(signalType).connect(name, consumer);
    }

    @SuppressWarnings("unchecked")
    private <T extends ArnoldSignal> ConsumingSyncSignal<T> signal(final Class<T> signalType) {
        return (ConsumingSyncSignal<T>) signals.computeIfAbsent(signalType, ignored ->
                ConsumingSyncSignal.<T>builder()
                        .errorHandler(failure -> {
                            throw new IllegalStateException("Arnold signal handler failed", failure);
                        })
                        .build());
    }
}
