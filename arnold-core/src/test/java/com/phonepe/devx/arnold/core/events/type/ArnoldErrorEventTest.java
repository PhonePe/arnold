package com.phonepe.devx.arnold.core.events.type;

import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.events.EventType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ArnoldErrorEventTest {

    @Test
    void testGetEventType() {
        ArnoldErrorEvent event = ArnoldErrorEvent.builder()
                .errorCode(ArnoldErrorCode.INTERNAL_SERVER_ERROR)
                .message("Some error message")
                .build();
        Assertions.assertEquals(EventType.EXCEPTION, event.getEventType(), "EventType should be EXCEPTION");
    }

    @Test
    void testGetErrorCode() {
        ArnoldErrorEvent event = ArnoldErrorEvent.builder()
                .errorCode(ArnoldErrorCode.INTERNAL_SERVER_ERROR)
                .message("Some error message")
                .build();
        Assertions.assertEquals(ArnoldErrorCode.INTERNAL_SERVER_ERROR, event.getErrorCode(), "ErrorCode should be INTERNAL_SERVER_ERROR");
    }

    @Test
    void testGetMessage() {
        ArnoldErrorEvent event = ArnoldErrorEvent.builder()
                .errorCode(ArnoldErrorCode.INTERNAL_SERVER_ERROR)
                .message("Some error message")
                .build();
        Assertions.assertEquals("Some error message", event.getMessage(), "Message should be 'Some error message'");
    }
}
