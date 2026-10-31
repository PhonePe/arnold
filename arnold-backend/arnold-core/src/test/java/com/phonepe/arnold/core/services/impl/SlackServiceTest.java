/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.arnold.core.services.impl;

import com.codahale.metrics.MetricRegistry;
import com.phonepe.arnold.core.config.SlackClientConfig;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.users.UsersLookupByEmailRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.users.UsersLookupByEmailResponse;
import com.slack.api.model.block.DividerBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlackServiceTest {
    private final MethodsClient methods = mock(MethodsClient.class);
    private SlackService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new SlackService(null, new MetricRegistry());
        final var field = SlackService.class.getDeclaredField("slackClient");
        field.setAccessible(true);
        field.set(service, methods);
    }

    @Test
    void testConstructor_shouldNotCreateClient_whenConfigDisabled() throws Exception {
        final var disabled = new SlackClientConfig();
        disabled.setEnabled(false);

        assertNotNull(new SlackService(disabled, new MetricRegistry()));
    }

    @Test
    void testUsersLookupByEmail_shouldReturnClientResponse() throws Exception {
        final var response = new UsersLookupByEmailResponse();
        response.setOk(true);
        when(methods.usersLookupByEmail(any(UsersLookupByEmailRequest.class))).thenReturn(response);

        final var result = service.usersLookupByEmail("a@b.com");

        assertTrue(result.isOk());
        assertTrue(result.getHttpResponseHeaders().isEmpty());
        final var captor = ArgumentCaptor.forClass(UsersLookupByEmailRequest.class);
        verify(methods).usersLookupByEmail(captor.capture());
        assertEquals("a@b.com", captor.getValue().getEmail());
    }

    @Test
    void testChatPostMessage_shouldSendMarkdownText() throws Exception {
        final var response = new ChatPostMessageResponse();
        response.setError("channel_not_found");
        when(methods.chatPostMessage(any(ChatPostMessageRequest.class))).thenReturn(response);

        final var result = service.chatPostMessage("C1", "hello");

        assertEquals("channel_not_found", result.getError());
        final var captor = ArgumentCaptor.forClass(ChatPostMessageRequest.class);
        verify(methods).chatPostMessage(captor.capture());
        assertEquals("C1", captor.getValue().getChannel());
        assertEquals("hello", captor.getValue().getText());
        assertTrue(captor.getValue().isMrkdwn());
    }

    @Test
    void testChatPostBlockMessage_shouldSendBlocks() throws Exception {
        final var response = new ChatPostMessageResponse();
        response.setOk(true);
        when(methods.chatPostMessage(any(ChatPostMessageRequest.class))).thenReturn(response);

        final var result = service.chatPostBlockMessage("C1", List.of(new DividerBlock()));

        assertTrue(result.isOk());
        final var captor = ArgumentCaptor.forClass(ChatPostMessageRequest.class);
        verify(methods).chatPostMessage(captor.capture());
        assertEquals(1, captor.getValue().getBlocks().size());
    }

    @Test
    void testCalls_shouldPropagateCheckedClientExceptions() throws Exception {
        when(methods.usersLookupByEmail(any(UsersLookupByEmailRequest.class))).thenThrow(new IOException("down"));
        when(methods.chatPostMessage(any(ChatPostMessageRequest.class))).thenThrow(mock(SlackApiException.class));

        assertThrows(IOException.class, () -> service.usersLookupByEmail("a@b.com"));
        assertThrows(SlackApiException.class, () -> service.chatPostMessage("C1", "x"));
        assertThrows(SlackApiException.class, () -> service.chatPostBlockMessage("C1", List.of()));
    }
}
