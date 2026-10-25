package com.phonepe.arnold.core.events;

public interface ArnoldSignalPublisher {
    <T extends ArnoldSignal> void publish(T event);
}
