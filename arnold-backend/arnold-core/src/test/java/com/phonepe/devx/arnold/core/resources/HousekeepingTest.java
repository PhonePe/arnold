package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.models.ArnoldResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HousekeepingTest {

    private Housekeeping housekeeping;

    @BeforeEach
    void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @DisplayName("Should return 'prefix + message' string")
    void testSync() {
        String prefix = "Hello";
        String message = "JUnit5";
        ArnoldResponse<String> response = housekeeping.sync(prefix, message);
        assertEquals(String.format("%s %s", prefix, message), response.getResponse());
    }

    @Test
    @DisplayName("Should return default message when message query param is not provided")
    void testSyncDefaultMessage() {
        String prefix = "Hello";
        ArnoldResponse<String> response = housekeeping.sync(prefix, null);
        assertEquals(String.format("%s null", prefix), response.getResponse());
    }
}
