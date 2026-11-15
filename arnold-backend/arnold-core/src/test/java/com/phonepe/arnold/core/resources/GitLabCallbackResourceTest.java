/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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

package com.phonepe.arnold.core.resources;

import com.phonepe.arnold.core.services.GitLabCallbackService;
import com.phonepe.arnold.models.callback.CallbackResponse;
import com.phonepe.arnold.models.callback.GitLabWebhookRequest;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(DropwizardExtensionsSupport.class)
class GitLabCallbackResourceTest {
    private static final GitLabCallbackService SERVICE = mock(GitLabCallbackService.class);
    private static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addResource(new GitLabCallbackResource(SERVICE))
            .build();

    @AfterEach
    void tearDown() {
        reset(SERVICE);
    }

    @Test
    void testProcess_shouldDelegateHeadersAndPayload_whenRequestIsValid() {
        final var request = new GitLabWebhookRequest("merge_request", null, null, null, null, null, null, null, null);
        when(SERVICE.process(any(), any(), any(), any())).thenReturn(new CallbackResponse("delivery-1", "processed"));

        final var response = RESOURCES.target("/gitlab-callbacks/v1/events")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header("X-Gitlab-Token", "secret")
                .header("X-Gitlab-Event-UUID", "delivery-1")
                .header("X-Gitlab-Event", "Merge Request Hook")
                .post(Entity.json(request));

        assertEquals(200, response.getStatus());
        verify(SERVICE).process("secret", "delivery-1", "Merge Request Hook", request);
    }
}
