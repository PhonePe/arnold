package com.phonepe.devx.arnold.core.events;

import com.phonepe.devx.arnold.core.model.events.ArnoldDomainEvent;
import io.appform.signals.signalhandlers.SignalConsumer;
import io.appform.signals.signals.ConsumingSyncSignal;

import javax.inject.Singleton;

@Singleton
public class ArnoldDomainEventBus {
    private final ConsumingSyncSignal<ArnoldDomainEvent> signal = new ConsumingSyncSignal<>();

    public void connect(final String name, final SignalConsumer<ArnoldDomainEvent> consumer) {
        signal.connect(name, consumer);
    }

    public void publish(final ArnoldDomainEvent event) {
        signal.dispatch(event);
    }
}
