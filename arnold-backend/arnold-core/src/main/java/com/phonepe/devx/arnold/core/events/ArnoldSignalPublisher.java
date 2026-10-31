package com.phonepe.devx.arnold.core.events;

public interface ArnoldSignalPublisher {
    <T extends ArnoldSignal> void publish(T event);
}
