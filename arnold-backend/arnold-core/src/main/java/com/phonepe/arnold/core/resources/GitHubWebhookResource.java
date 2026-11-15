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

import com.phonepe.arnold.core.provider.github.GitHubWebhookService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.callback.CallbackResponse;
import lombok.AllArgsConstructor;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * GitHub App webhook endpoint. Requests are authenticated by their HMAC signature, not by a bearer token.
 */
@Path("/github-callbacks/v1")
@Produces(MediaType.APPLICATION_JSON)
@AllArgsConstructor(onConstructor_ = @Inject)
public class GitHubWebhookResource {
    private final GitHubWebhookService webhooks;

    @POST
    @Path("/events")
    @Consumes(MediaType.WILDCARD)
    public ArnoldResponse<CallbackResponse> process(
            @HeaderParam("X-Hub-Signature-256") final String signature,
            @HeaderParam("X-GitHub-Delivery") final String deliveryId,
            @HeaderParam("X-GitHub-Event") final String event,
            final byte[] body) {
        return ArnoldResponse.ok(webhooks.process(signature, deliveryId, event, body == null ? new byte[0] : body));
    }
}
