package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.core.services.HousekeepingService;
import com.phonepe.devx.arnold.core.services.SampleService;
import com.phonepe.devx.arnold.models.es.BackfillResult;
import com.phonepe.devx.arnold.models.request.BackfillRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class HousekeepingTest {

    @Mock
    SampleService sampleService;

    @Mock
    HousekeepingService housekeepingService;

    private Housekeeping housekeeping;

    @BeforeEach
    void init() {
        MockitoAnnotations.initMocks(this);
        housekeeping = new Housekeeping(sampleService, housekeepingService);
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

    @Test
    @DisplayName("Should return sampleService result")
    void testSampleService() {
        String queryParam = "JUnit5";
        when(sampleService.sampleApi(anyString())).thenReturn(true);
        ArnoldResponse<Boolean> response = housekeeping.sampleService(queryParam);
        assertEquals(true, response.getResponse());
    }

    @Test
    @DisplayName("Should delegate backfill to housekeepingService")
    void testBackfill() {
        final BackfillResult expected = BackfillResult.builder()
                .dryRun(true)
                .scanned(5)
                .enrichable(3)
                .build();
        when(housekeepingService.backfillReviewFeedback(any(BackfillRequest.class))).thenReturn(expected);
        ArnoldResponse<BackfillResult> response =
                housekeeping.backfillReviewFeedback(BackfillRequest.builder().build());
        assertEquals(expected, response.getResponse());
    }
}
