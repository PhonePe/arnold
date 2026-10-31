package com.phonepe.devx.arnold.core.events;

import com.phonepe.platform.eventingestion.model.Event;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventMapperTest {

    @Test
    void testEventWithGroupingKey() {
        String groupingKey = "groupingKey";
        EventType eventType = EventType.EXCEPTION;
        String data = "user123";
        String APP_NAME = "arnold";
        Event<String> event = EventMapper.event(eventType, data, groupingKey);

        assertEquals(APP_NAME, event.getApp());
        assertNotNull(event.getId());
        assertEquals(EventMapper.DEFAULT_VERSION, event.getEventSchemaVersion());
        assertEquals(eventType.name(), event.getEventType());
        assertEquals(data, event.getEventData());
        assertTrue(Date.from(Instant.now()).getTime() - event.getTime().getTime() < 1000);
        assertEquals(groupingKey, event.getGroupingKey());
    }

    @Test
    void testEventWithoutGroupingKey() {
        EventType eventType = EventType.EXCEPTION;
        Integer data = 123;
        String APP_NAME = "arnold";
        Event<Integer> event = EventMapper.event(eventType, data, null);

        assertEquals(APP_NAME, event.getApp());
        assertNotNull(event.getId());
        assertEquals(EventMapper.DEFAULT_VERSION, event.getEventSchemaVersion());
        assertEquals(eventType.name(), event.getEventType());
        assertEquals(data, event.getEventData());
        assertTrue(Date.from(Instant.now()).getTime() - event.getTime().getTime() < 1000);
        assertNotNull(event.getGroupingKey());
    }

    @Test
    void testEventWithBaseEventAndGroupingKey() {
        String groupingKey = "groupingKey";
        String APP_NAME = "arnold";
        EventType eventType = EventType.EXCEPTION;
        BaseEvent data = () -> eventType;
        Event<BaseEvent> event = EventMapper.event(data, groupingKey);

        assertEquals(APP_NAME, event.getApp());
        assertNotNull(event.getId());
        assertEquals(EventMapper.DEFAULT_VERSION, event.getEventSchemaVersion());
        assertEquals(eventType.name(), event.getEventType());
        assertEquals(data, event.getEventData());
        assertTrue(Date.from(Instant.now()).getTime() - event.getTime().getTime() < 1000);
        assertEquals(groupingKey, event.getGroupingKey());
    }

    @Test
    void testEventWithBaseEventWithoutGroupingKey() {
        EventType eventType = EventType.EXCEPTION;
        BaseEvent data = () -> eventType;
        Event<BaseEvent> event = EventMapper.event(data);
        String APP_NAME = "arnold";

        assertEquals(APP_NAME, event.getApp());
        assertNotNull(event.getId());
        assertEquals(EventMapper.DEFAULT_VERSION, event.getEventSchemaVersion());
        assertEquals(eventType.name(), event.getEventType());
        assertEquals(data, event.getEventData());
        assertTrue(Date.from(Instant.now()).getTime() - event.getTime().getTime() < 1000);
        assertNotNull(event.getGroupingKey());
    }
}
