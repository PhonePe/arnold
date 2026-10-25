package com.phonepe.arnold.core.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConstantsTest {
    @Test
    void testHostname() {
        String expected = System.getenv("HOST");
        if (expected == null || expected.isEmpty()) {
            expected = "UNKNOWN";
        }

        String actual = Constants.HOSTNAME;
        assertEquals(expected, actual);
    }
}
