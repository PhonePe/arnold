package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.clients.ClientHttpExecutorFactory;
import com.phonepe.devx.arnold.core.events.EventIngestor;
import com.phonepe.devx.cibot.models.CIBotResponse;
import com.phonepe.devx.cibot.models.events.gitlab.Note;
import com.phonepe.devx.cibot.models.events.gitlab.Project;
import com.phonepe.devx.cibot.models.events.gitlab.User;
import com.phonepe.platform.http.v2.executor.HttpGetExecutor;
import com.phonepe.platform.http.v2.executor.factory.HttpExecutorBuilderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CibotServiceHttpImplTest {

    @Mock
    private EventIngestor mockEventIngestor;

    @Mock
    private HttpExecutorBuilderFactory mockExecutorFactory;

    @Mock
    private HttpGetExecutor mockHttpGetExecutor;

    private CibotServiceHttpImpl cibotService;
    private MockedStatic<ClientHttpExecutorFactory> mockedFactory;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockedFactory = mockStatic(ClientHttpExecutorFactory.class);
        mockedFactory.when(() -> ClientHttpExecutorFactory.getExecutor("CIBOT"))
                .thenReturn(mockExecutorFactory);
        cibotService = new CibotServiceHttpImpl(mockEventIngestor);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockedFactory != null) mockedFactory.close();
        if (mocks != null) mocks.close();
    }

    private HttpGetExecutor.HttpGetExecutorBuilder stubBuilder() {
        final var builder = mock(HttpGetExecutor.HttpGetExecutorBuilder.class);
        when(builder.url(anyString())).thenReturn(builder);
        when(builder.responseTypeReference(any())).thenReturn(builder);
        when(builder.command(anyString())).thenReturn(builder);
        when(builder.nonSuccessResponseConsumer(any())).thenReturn(builder);
        when(builder.build()).thenReturn(mockHttpGetExecutor);
        when(mockExecutorFactory.httpGetExecutorBuilder()).thenReturn(builder);
        return builder;
    }

    @Nested
    class GetProjectByGitlabId {

        @Test
        void shouldReturnProject_whenResponseIsSuccess() {
            stubBuilder();
            final var project = Project.builder().id(10).name("test-project").build();
            when(mockHttpGetExecutor.executeTracked()).thenReturn(CIBotResponse.ok(project));

            final var result = cibotService.getProjectByGitlabId(10);

            assertTrue(result.isPresent());
            assertEquals(project, result.get());
        }

        @Test
        void shouldReturnEmpty_whenResponseIsNonSuccess() {
            stubBuilder();
            when(mockHttpGetExecutor.executeTracked())
                    .thenReturn(CIBotResponse.error(404, "not found"));

            final var result = cibotService.getProjectByGitlabId(10);

            assertTrue(result.isEmpty());
        }

        @Test
        void shouldReturnEmpty_whenExecutorThrowsException() {
            stubBuilder();
            when(mockHttpGetExecutor.executeTracked())
                    .thenThrow(new RuntimeException("connection refused"));

            final var result = cibotService.getProjectByGitlabId(10);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class GetUserByGitlabId {

        @Test
        void shouldReturnUser_whenResponseIsSuccess() {
            stubBuilder();
            final var user = User.builder().id(100).name("Reviewer").build();
            when(mockHttpGetExecutor.executeTracked()).thenReturn(CIBotResponse.ok(user));

            final var result = cibotService.getUserByGitlabId(100);

            assertTrue(result.isPresent());
            assertEquals(user, result.get());
        }

        @Test
        void shouldReturnEmpty_whenResponseIsNonSuccess() {
            stubBuilder();
            when(mockHttpGetExecutor.executeTracked())
                    .thenReturn(CIBotResponse.error(403, "forbidden"));

            final var result = cibotService.getUserByGitlabId(100);

            assertTrue(result.isEmpty());
        }

        @Test
        void shouldReturnEmpty_whenExecutorThrowsException() {
            stubBuilder();
            when(mockHttpGetExecutor.executeTracked())
                    .thenThrow(new RuntimeException("timeout"));

            final var result = cibotService.getUserByGitlabId(100);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class GetMergeRequestNote {

        @Test
        void shouldReturnNote_whenResponseIsSuccess() {
            stubBuilder();
            final var note = Note.builder().id(55).body("looks good").build();
            when(mockHttpGetExecutor.executeTracked()).thenReturn(CIBotResponse.ok(note));

            final var result = cibotService.getMergeRequestNote(10, 25, 55);

            assertTrue(result.isPresent());
            assertEquals(note, result.get());
        }

        @Test
        void shouldReturnEmpty_whenResponseIsNonSuccess() {
            stubBuilder();
            when(mockHttpGetExecutor.executeTracked())
                    .thenReturn(CIBotResponse.error(404, "note not found"));

            final var result = cibotService.getMergeRequestNote(10, 25, 55);

            assertTrue(result.isEmpty());
        }

        @Test
        void shouldReturnEmpty_whenExecutorThrowsException() {
            stubBuilder();
            when(mockHttpGetExecutor.executeTracked())
                    .thenThrow(new RuntimeException("ES unavailable"));

            final var result = cibotService.getMergeRequestNote(10, 25, 55);

            assertTrue(result.isEmpty());
        }

        @Test
        void shouldCallCorrectUrl() {
            final var builder = stubBuilder();
            when(mockHttpGetExecutor.executeTracked()).thenReturn(null);

            cibotService.getMergeRequestNote(10, 25, 55);

            verify(builder).url("/notes/projectId/10/mrIid/25/noteId/55");
        }
    }
}
