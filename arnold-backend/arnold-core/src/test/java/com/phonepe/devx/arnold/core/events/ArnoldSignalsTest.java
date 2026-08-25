package com.phonepe.devx.arnold.core.events;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArnoldSignalsTest {
    @Test
    void testPublish_shouldDispatchOnlyToSubscribersOfTheSignalType() {
        final var signals = new ArnoldSignals();
        final var first = new ArrayList<String>();
        final var second = new ArrayList<String>();
        signals.subscribe(FirstSignal.class, "first", signal -> first.add(signal.id()));
        signals.subscribe(SecondSignal.class, "second", signal -> second.add(signal.id()));

        signals.publish(new FirstSignal("one", 10));

        assertEquals(java.util.List.of("one"), first);
        assertEquals(java.util.List.of(), second);
    }

    @Test
    void testPublish_shouldPropagateSubscriberFailure() {
        final var signals = new ArnoldSignals();
        signals.subscribe(FirstSignal.class, "failing", signal -> {
            throw new IllegalArgumentException("failure");
        });

        assertThrows(IllegalStateException.class, () -> signals.publish(new FirstSignal("one", 10)));
    }

    private record FirstSignal(String id, long occurredAt) implements ArnoldSignal {
    }

    private record SecondSignal(String id, long occurredAt) implements ArnoldSignal {
    }
}
