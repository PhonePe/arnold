package com.phonepe.devx.arnold.core.services;

import com.phonepe.devx.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.services.copilot.CopilotConnectionService;
import com.phonepe.devx.arnold.models.analysis.ExecutionOptions;
import com.phonepe.devx.arnold.models.analysis.HarnessOption;
import com.phonepe.devx.arnold.models.integration.CopilotConnectionResponse;
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
        final var service = new AnalysisExecutionService(config, connections);
        when(connections.status("alice")).thenReturn(new CopilotConnectionResponse(true, true, "alice-gh", "connected", null, null));
        when(connections.status("bob")).thenReturn(new CopilotConnectionResponse(true, false, null, "disconnected", null, null));
        when(connections.models("alice")).thenReturn(List.of(new HarnessOption.Model("allowed", "Allowed model", false)));
        assertEquals(List.of("copilot"), service.options("alice").stream().map(HarnessOption::id).toList());
        assertEquals("allowed", service.options("alice").get(0).models().get(0).id());
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
        verifyNoInteractions(connections);
    }
}
