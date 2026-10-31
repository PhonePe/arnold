package com.phonepe.devx.arnold.core.events;

import io.appform.signals.signalhandlers.SignalConsumer;

public interface ArnoldSignalSubscriber {
    <T extends ArnoldSignal> void subscribe(Class<T> signalType, String name, SignalConsumer<T> consumer);
}
