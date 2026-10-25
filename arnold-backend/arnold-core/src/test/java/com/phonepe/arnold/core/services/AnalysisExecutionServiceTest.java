package com.phonepe.arnold.core.services;

import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.services.copilot.CopilotConnectionService;
import com.phonepe.arnold.models.analysis.ExecutionOptions;
import com.phonepe.arnold.models.analysis.HarnessOption;
import com.phonepe.arnold.models.integration.CopilotConnectionResponse;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisExecutionServiceTest {
    @Test
    void catalogIsScopedToTheConnectedUserAndValidatesTheCurrentModels() {
        final var config = new ArnoldCoreConfiguration();
        config.getCopilotConfiguration().setEnabled(true);
        config.getCopilotConfiguration().setJobTokenExchangeEnabled(true);
        final var connections = mock(CopilotConnectionService.class);
        final var catalog = mock(ModelCatalog.class);
        when(catalog.models()).thenReturn(List.of());
        final var service = new AnalysisExecutionService(config, connections, catalog);
        when(connections.status("alice")).thenReturn(new CopilotConnectionResponse(true, true, "alice-gh", "connected", null, null));
        when(connections.status("bob")).thenReturn(new CopilotConnectionResponse(true, false, null, "disconnected", null, null));
        when(connections.models("alice")).thenReturn(List.of(new HarnessOption.Model("allowed", "Allowed model", false)));
        assertEquals(List.of("copilot", "opencode"), service.options("alice").stream().map(HarnessOption::id).toList());
        assertEquals("allowed", service.options("alice").get(0).models().get(0).id());
        assertFalse(service.options("alice").get(1).available());
        service.validate("alice", new ExecutionOptions("copilot", "allowed"));
        assertThrows(ArnoldError.class, () -> service.validate("alice", new ExecutionOptions("copilot", "unavailable")));
        assertThrows(ArnoldError.class, () -> service.validate("alice", new ExecutionOptions("opencode", "provider/model")));
        assertFalse(service.options("bob").get(0).available());
        assertTrue(service.options("bob").get(0).models().isEmpty());
        verify(connections, never()).models("bob");
        when(connections.models("alice")).thenReturn(List.of());
        assertThrows(ArnoldError.class, () -> service.validate("alice", new ExecutionOptions("copilot", "allowed")));
        when(connections.models("alice")).thenThrow(ArnoldError.raise(ArnoldErrorCode.CLIENT_FAILURE).build());
        assertFalse(service.options("alice").get(0).available());
        config.getCopilotConfiguration().setJobTokenExchangeEnabled(false);
        clearInvocations(connections);
        assertFalse(service.options("alice").get(0).available());
        verify(connections, never()).models("alice");
    }

    @Test
    void opencodeListsCatalogModelsWithoutUserConnection() {
        final var config = new ArnoldCoreConfiguration();
        config.getOpenCodeConfiguration().setEnabled(true);
        config.getOpenCodeConfiguration().setProvider("gateway");
        config.getOpenCodeConfiguration().setDefaultModel("gateway/gpt-4.1");
        final var catalog = mock(ModelCatalog.class);
        when(catalog.models()).thenReturn(List.of(new ModelCatalog.CatalogModel("gpt-4.1", "GPT 4.1")));
        final var service = new AnalysisExecutionService(config, mock(CopilotConnectionService.class), catalog);
        final var option = service.options("alice").get(1);
        assertEquals("opencode", option.id());
        assertTrue(option.available());
        assertEquals(List.of("gateway/gpt-4.1"), option.models().stream().map(HarnessOption.Model::id).toList());
        assertTrue(option.models().get(0).isDefault());
        service.validate("alice", new ExecutionOptions("opencode", "gateway/gpt-4.1"));
        assertThrows(ArnoldError.class, () -> service.validate("alice", new ExecutionOptions("opencode", "gateway/unknown")));
        config.getOpenCodeConfiguration().setEnabled(false);
        assertFalse(service.options("alice").get(1).available());
        assertTrue(service.options("alice").get(1).models().isEmpty());
    }
}
