package com.phonepe.devx.arnold.core.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.phonepe.devx.arnold.core.models.feedback.RuleGenerationExecution;
import com.phonepe.devx.arnold.core.models.feedback.RuleGenerationStatus;
import com.phonepe.devx.arnold.models.request.TargetScope;
import com.phonepe.devx.arnold.core.elastic.ESStorageManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleGenerationExecutionStoreImplTest {

    @Mock
    private ESStorageManager storageManager;

    private RuleGenerationExecutionStoreImpl store;

    @BeforeEach
    void setUp() {
        store = new RuleGenerationExecutionStoreImpl(storageManager);
    }

    @Test
    void save_shouldDelegateToStorageManager_withCorrectIndexAndId() {
        final var execution = RuleGenerationExecution.builder()
                .executionId("exec-1")
                .targetPath("devx")
                .scope(TargetScope.GROUP)
                .status(RuleGenerationStatus.REQUESTED)
                .createdAt(1L)
                .updatedAt(1L)
                .build();

        store.save(execution);

        verify(storageManager).save("RULE_GENERATION_RUNS", "exec-1", execution);
    }

    @Test
    void get_shouldReturnExecution_whenStorageManagerReturnsValue() {
        final var execution = RuleGenerationExecution.builder()
                .executionId("exec-1")
                .status(RuleGenerationStatus.TRIGGERED)
                .build();
        when(storageManager.get(eq("RULE_GENERATION_RUNS"), eq("exec-1"), any(TypeReference.class)))
                .thenReturn(Optional.of(execution));

        final var result = store.get("exec-1");

        assertTrue(result.isPresent());
        assertEquals("exec-1", result.get().getExecutionId());
        assertEquals(RuleGenerationStatus.TRIGGERED, result.get().getStatus());
    }

    @Test
    void get_shouldReturnEmpty_whenStorageManagerReturnsEmpty() {
        when(storageManager.get(eq("RULE_GENERATION_RUNS"), eq("missing"), any(TypeReference.class)))
                .thenReturn(Optional.empty());

        final var result = store.get("missing");

        assertTrue(result.isEmpty());
    }
}
