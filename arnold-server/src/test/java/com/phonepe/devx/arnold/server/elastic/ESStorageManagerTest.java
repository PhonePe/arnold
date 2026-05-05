package com.phonepe.devx.arnold.server.elastic;

import com.phonepe.commons.columbus.Columbus;
import com.phonepe.commons.columbus.storage.StorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ESStorageManagerTest {

    @Mock
    private Columbus columbus;

    @Mock
    private StorageClient storageClient;

    private ESStorageManager esStorageManager;

    @BeforeEach
    void setUp() {
        when(columbus.storage("arnold-es-cluster")).thenReturn(storageClient);
        esStorageManager = new ESStorageManager(columbus);
    }

    @Test
    void save_shouldExecuteUpsertCommand_onHappyPath() {
        final var document = new Object();

        esStorageManager.save("CORE_REVIEWS", "10:20:55", document);

        verify(storageClient).execute(any());
    }

    @Test
    void save_shouldPropagateException_whenStorageClientThrows() {
        doThrow(new RuntimeException("ES unavailable"))
                .when(storageClient).execute(any());

        assertThrows(RuntimeException.class,
                () -> esStorageManager.save("CORE_REVIEWS", "10:20:55", new Object()));
    }
}
