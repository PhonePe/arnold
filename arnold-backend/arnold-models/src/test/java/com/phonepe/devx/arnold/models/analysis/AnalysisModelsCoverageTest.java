package com.phonepe.devx.arnold.models.analysis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisModelsCoverageTest {

    @Test
    void testTokenUsage_shouldDefaultOptionalFields_whenUsingShortConstructors() {
        final var withUrl = new TokenUsage(1, 2, null, "https://job");
        final var plain = new TokenUsage(1, 2, List.of(new TokenUsage.Model("m", 1, 2)));

        assertTrue((withUrl.models()).isEmpty());
        assertEquals("https://job", withUrl.jobUrl());
        assertNull(withUrl.cacheReadTokens());
        assertNull(plain.jobUrl());
        assertNull(plain.models().get(0).cacheReadTokens());
    }

    @Test
    void testTokenUsageMerge_shouldSumPerModel_whenMultipleUsages() {
        final var a = new TokenUsage(10, 5, List.of(new TokenUsage.Model("b", 4, 2, 1L),
                new TokenUsage.Model("a", 6, 3)), "https://a", 1L);
        final var b = new TokenUsage(20, 10, List.of(new TokenUsage.Model("b", 8, 4, 2L)), "https://b", null);

        final var merged = TokenUsage.merge(Arrays.asList(a, null, b));

        assertEquals(30, merged.inputTokens());
        assertEquals(15, merged.outputTokens());
        assertNull(merged.jobUrl());
        assertEquals(1L, merged.cacheReadTokens());
        assertEquals(java.util.List.of(new TokenUsage.Model("a", 6, 3, null),
                new TokenUsage.Model("b", 12, 6, 3L)), (merged.models()));
    }

    @Test
    void testTokenUsageMerge_shouldKeepJobUrl_whenSingleUsage() {
        final var only = new TokenUsage(1, 1, List.of(), "https://only");

        assertEquals("https://only", TokenUsage.merge(List.of(only)).jobUrl());
        assertNull(TokenUsage.merge(new ArrayList<>(Arrays.asList((TokenUsage) null))));
        assertNull(TokenUsage.sum(java.util.stream.Stream.of((Long) null)));
        assertEquals(3L, TokenUsage.sum(java.util.stream.Stream.of(1L, null, 2L)));
    }

    @Test
    void testGroupAnalysisResponse_shouldFillDefaults_whenUsingShortConstructors() {
        final var options = new ExecutionOptions("copilot", "gpt");
        final var base = new GroupAnalysisResponse("r", "g", "n", "e", "running", List.of(), Map.of(), List.of(),
                null, "d", 1L, "url", false, 1, 2);
        final var withExec = new GroupAnalysisResponse("r", "g", "n", "e", "running", List.of(), Map.of(),
                List.of(), List.of(), "d", 1L, "url", true, 1, 2, options);

        assertTrue((base.reviewPersonas()).isEmpty());
        assertNull(base.execution());
        assertNull(base.usage());
        assertNull(base.invokedBy());
        assertEquals(options, withExec.execution());
        assertEquals("me", withExec.withInvokedBy("me").invokedBy());
    }

    @Test
    void testGroupAnalysisStartRequestAndUpdate_shouldDefaultNulls_whenUsingShortConstructors() {
        final var start = new GroupAnalysisResponse.StartRequest("rfc", List.of("J-1"), "ctx", List.of("p"));
        final var update = new GroupAnalysisResponse.Update("e", "w", 1, "running", Map.of(), List.of(), "d",
                Map.of(), true);

        assertNull(start.execution());
        assertEquals(java.util.List.of("J-1"), (start.jiraTickets()));
        assertNull(update.usage());
        assertTrue(update.analysisComplete());
    }

    @Test
    void testAnalysisResponse_shouldCopyListsAndDefaults_whenBuilt() {
        final var finding = new AnalysisFindingResponse("f", "sha", "a.java", null, 1, "body", "posted", null);
        final var response = new AnalysisResponse("mr", "state", "run", List.of("p"), "completed", 1, 2L, "url",
                "ok", "detail", null, List.of(finding), 1, 2, null);
        final var empty = new AnalysisResponse("mr", null, null, null, null, null, null, null, 0, null, null, null,
                null, null, null, 0, 0, null, null);

        assertEquals(java.util.List.of(finding), (response.findings()));
        assertTrue((response.reviewPersonas()).isEmpty());
        assertNull(response.repositoryPath());
        assertNull(response.usage());
        assertTrue((empty.findings()).isEmpty());
        assertNull(finding.personaId());
    }
}
