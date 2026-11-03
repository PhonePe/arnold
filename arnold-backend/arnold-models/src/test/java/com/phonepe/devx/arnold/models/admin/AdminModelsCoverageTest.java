package com.phonepe.devx.arnold.models.admin;

import com.phonepe.devx.arnold.models.analysis.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdminModelsCoverageTest {

    @Test
    void testReviewFeedbackMetricsResponse_shouldExposeComponents_whenBuilt() {
        final var score = new ReviewFeedbackMetricsResponse.Score(1, 2.0, 1.0, 3.0);
        final var origin = new ReviewFeedbackMetricsResponse.Origin(10, 4, 8, 50.0, score, Map.of("a", 1L),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(new ReviewFeedbackMetricsResponse.Week("w", 5, 2, 40.0)));
        final var authors = new ReviewFeedbackMetricsResponse.Authors(9, 8, 4, 50.0, 3, 2,
                List.of(new ReviewFeedbackMetricsResponse.CommentWeek("w", 9)), origin);
        final var response = new ReviewFeedbackMetricsResponse(1, 2, origin, origin,
                new ReviewFeedbackMetricsResponse.Authorship("bot", authors, authors),
                new ReviewFeedbackMetricsResponse.PersonaSplit(origin, origin), null, null, null);

        assertEquals(3.0, response.arnold().score().max());
        assertEquals("w", response.arnold().weekly().get(0).weekStart());
        assertEquals(9, response.authorship().ai().weekly().get(0).total());
        assertEquals(50.0, response.authorship().human().resolutionRate());
        assertEquals(1L, (response.personaSplit().general().acceptance()).get("a"));
    }

    @Test
    void testTokenUsageMetricsResponse_shouldExposeComponents_whenBuilt() {
        final var usage = new TokenUsage(1, 2, List.of());
        final var response = new TokenUsageMetricsResponse(1, 2, 3, 4, 5, 6,
                List.of(new TokenUsageMetricsResponse.FlowUsage("improve", 1, 2, 3, 4)),
                List.of(new TokenUsageMetricsResponse.ModelUsage("m", 1, 2, 3, 4)),
                List.of(new TokenUsageMetricsResponse.Job("id", "f", "r", "t", "u", "h", "s", usage, 1, 2)));

        assertEquals("improve", response.flows().get(0).flow());
        assertEquals("m", response.models().get(0).model());
        assertSame(usage, response.recentJobs().get(0).usage());
        assertEquals(6, response.cacheReadTokens());
    }
}
