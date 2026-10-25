package com.phonepe.arnold.core.runner;

import com.phonepe.arnold.core.ArnoldCoreConfiguration;
import com.phonepe.arnold.core.config.ClioPipelineConfiguration;
import com.phonepe.arnold.core.config.GitLabConfiguration;
import com.phonepe.arnold.core.services.copilot.CopilotExecutionService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RunnerBackendsTest {

    @Test
    void defaultsToLocalMode() {
        assertEquals(RunnerMode.LOCAL, RunnerBackends.effective(new ArnoldCoreConfiguration()).getMode());
    }

    @Test
    void mapsLegacyClioBlockToGitLabMode() {
        final var config = new ArnoldCoreConfiguration();
        config.setGitlabConfiguration(new GitLabConfiguration("https://gitlab.example.com", "id", "secret", "cb",
                1, 1, 1000, new ClioPipelineConfiguration(7, "acme/runner", "main", "token")));

        final var runner = RunnerBackends.effective(config);

        assertEquals(RunnerMode.GITLAB, runner.getMode());
        assertEquals("https://gitlab.example.com", runner.getGitlab().getBaseUrl());
        assertEquals(7, runner.getGitlab().getProjectId());
        assertEquals("token", runner.getGitlab().getTriggerToken());
    }

    @Test
    void explicitRunnerBlockInheritsGitLabBaseUrl() {
        final var config = new ArnoldCoreConfiguration();
        config.setGitlabConfiguration(new GitLabConfiguration("https://gitlab.example.com", "id", "secret", "cb",
                1, 1, 1000));
        final var runner = new RunnerConfiguration();
        runner.setMode(RunnerMode.GITLAB);
        config.setRunner(runner);

        assertEquals("https://gitlab.example.com", RunnerBackends.effective(config).getGitlab().getBaseUrl());
    }

    @Test
    void dispatcherRevokesExecutionCredentialsWhenSubmissionFails() {
        final var backends = mock(RunnerBackends.class);
        final var backend = mock(RunnerBackend.class);
        final var copilot = mock(CopilotExecutionService.class);
        final var variables = Map.of("WORKFLOW", "improve", CopilotExecutionService.KEY_VARIABLE, "k");
        when(copilot.pipelineVariables(any())).thenReturn(variables);
        when(backends.get()).thenReturn(backend);
        when(backend.submit(variables)).thenThrow(new IllegalStateException("down"));
        final var dispatcher = new RunnerDispatcherImpl(backends, copilot);

        assertThrows(IllegalStateException.class, () -> dispatcher.submit(Map.of("WORKFLOW", "improve")));
        verify(copilot).revoke(variables);
    }

    @Test
    void jobUrlIsNullWhenRunnerIsMisconfigured() {
        final var backends = mock(RunnerBackends.class);
        when(backends.get()).thenThrow(new IllegalStateException("misconfigured"));

        assertNull(new RunnerDispatcherImpl(backends, mock(CopilotExecutionService.class)).jobUrl(1));
    }
}
