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

package com.phonepe.arnold.core.resources;

import com.phonepe.arnold.core.common.Constants;
import com.phonepe.arnold.core.services.GitLabCallbackService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.callback.CallbackResponse;
import com.phonepe.arnold.models.callback.GitLabWebhookRequest;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/gitlab-callbacks/v1")
@Tag(name = "GitLab Callback APIs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = Constants.BEARER_AUTH)
@SecurityScheme(name = Constants.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer")
@AllArgsConstructor(onConstructor_ = @Inject)
public class GitLabCallbackResource {
    private final GitLabCallbackService callbackService;

    @POST
    @Path("/events")
    public ArnoldResponse<CallbackResponse> process(
            @HeaderParam("X-Gitlab-Token") final String token,
            @HeaderParam("X-Gitlab-Event-UUID") final String deliveryId,
            @HeaderParam("X-Gitlab-Event") final String eventType,
            @Valid final GitLabWebhookRequest request) {
        return ArnoldResponse.ok(callbackService.process(token, deliveryId, eventType, request));
    }
}
