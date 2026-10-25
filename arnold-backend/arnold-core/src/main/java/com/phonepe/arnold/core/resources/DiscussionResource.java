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

import com.phonepe.arnold.core.auth.AuthenticatedUser;
import com.phonepe.arnold.core.common.Constants;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.CommentService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.comment.CommentResponse;
import com.phonepe.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.arnold.models.comment.PublishReviewRequest;
import com.phonepe.arnold.models.comment.ResolveDiscussionRequest;
import com.phonepe.arnold.models.comment.UpdateDraftRequest;
import com.phonepe.arnold.models.comment.ApplySuggestionRequest;
import com.phonepe.arnold.core.auth.ArnoldPrincipal;
import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/discussions/v1")
@Tag(name = "Discussion APIs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = Constants.BEARER_AUTH)
@SecurityScheme(name = Constants.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer")
@AllArgsConstructor(onConstructor_ = @Inject)
public class DiscussionResource {
    private final CommentService commentService;

    @GET
    @PermitAll
    @Operation(description = "List merge request discussions")
    public ArnoldResponse<List<CommentResponse>> list(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user,
            @QueryParam("provider") @DefaultValue("GITLAB") final String provider,
            @Positive @QueryParam("project_id") final long projectId,
            @Positive @QueryParam("mr_iid") final long mrIid) {
        return ArnoldResponse.ok(commentService.list(provider(provider), projectId, mrIid, AuthenticatedUser.id(user)));
    }

    @GET
    @Path("/drafts")
    @PermitAll
    @Operation(description = "List GitLab draft notes for the current review")
    public ArnoldResponse<List<CommentResponse>> listDrafts(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user,
            @QueryParam("provider") @DefaultValue("GITLAB") final String provider,
            @Positive @QueryParam("project_id") final long projectId,
            @Positive @QueryParam("mr_iid") final long mrIid) {
        return ArnoldResponse.ok(commentService.listDrafts(provider(provider), projectId, mrIid, AuthenticatedUser.id(user)));
    }

    @POST
    @PermitAll
    @Operation(description = "Create a GitLab discussion")
    public ArnoldResponse<CommentResponse> create(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user,
            @Valid final CreateDiscussionRequest request) {
        return ArnoldResponse.ok(commentService.create(request, AuthenticatedUser.id(user)));
    }

    @POST
    @Path("/drafts")
    @PermitAll
    @Operation(description = "Add a GitLab draft note to the current review")
    public ArnoldResponse<CommentResponse> createDraft(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user,
            @Valid final CreateDiscussionRequest request) {
        return ArnoldResponse.ok(commentService.createDraft(request, AuthenticatedUser.id(user)));
    }

    @PUT
    @Path("/drafts/{draftId}")
    @PermitAll
    @Operation(description = "Update a GitLab draft note")
    public ArnoldResponse<CommentResponse> updateDraft(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user,
            @Positive @PathParam("draftId") final long draftId,
            @Valid final UpdateDraftRequest request) {
        return ArnoldResponse.ok(commentService.updateDraft(draftId, request, AuthenticatedUser.id(user)));
    }

    @DELETE
    @Path("/drafts/{draftId}")
    @PermitAll
    @Operation(description = "Discard a GitLab draft note")
    public ArnoldResponse<Boolean> deleteDraft(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user,
            @QueryParam("provider") @DefaultValue("GITLAB") final String provider,
            @Positive @PathParam("draftId") final long draftId,
            @Positive @QueryParam("project_id") final long projectId,
            @Positive @QueryParam("mr_iid") final long mrIid) {
        commentService.deleteDraft(provider(provider), draftId, projectId, mrIid, AuthenticatedUser.id(user));
        return ArnoldResponse.ok(true);
    }

    @POST
    @Path("/drafts/publish")
    @PermitAll
    @Operation(description = "Publish all GitLab draft notes for the current review")
    public ArnoldResponse<List<CommentResponse>> publishDrafts(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user,
            @Valid final PublishReviewRequest request) {
        return ArnoldResponse.ok(commentService.publishDrafts(request, AuthenticatedUser.id(user)));
    }

    @PUT
    @Path("/{discussionId}/resolution")
    @PermitAll
    @Operation(description = "Resolve or reopen a GitLab discussion")
    public ArnoldResponse<Boolean> resolveDiscussion(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user,
            @NotBlank @PathParam("discussionId") final String discussionId,
            @Valid final ResolveDiscussionRequest request) {
        commentService.resolveDiscussion(discussionId, request, AuthenticatedUser.id(user));
        return ArnoldResponse.ok(true);
    }

    @PUT
    @Path("/suggestions/{suggestionId}/apply")
    @PermitAll
    @Operation(description = "Apply a GitLab code suggestion")
    public ArnoldResponse<Boolean> applySuggestion(
            @Parameter(hidden = true) @Auth final ArnoldPrincipal user,
            @QueryParam("provider") @DefaultValue("GITLAB") final String provider,
            @Positive @PathParam("suggestionId") final long suggestionId,
            final ApplySuggestionRequest request) {
        commentService.applySuggestion(provider(provider), suggestionId, request == null ? null : request.commitMessage(),
                AuthenticatedUser.id(user));
        return ArnoldResponse.ok(true);
    }

    private static VersionControlProviderType provider(final String value) {
        return VersionControlProviderType.parse(value);
    }
}
