package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.auth.AuthenticatedUser;
import com.phonepe.devx.arnold.core.common.Constants;
import com.phonepe.devx.arnold.core.services.CommentService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.comment.CommentResponse;
import com.phonepe.devx.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.olympus.im.client.security.ServiceUserPrincipal;
import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/discussions/v1")
@Tag(name = "Discussion APIs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = Constants.OLYMPUS)
@SecurityScheme(name = Constants.OLYMPUS, type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = Constants.AUTHORIZATION_HEADER)
@AllArgsConstructor(onConstructor_ = @Inject)
public class DiscussionResource {
    private final CommentService commentService;

    @GET
    @PermitAll
    @Operation(description = "List merge request discussions")
    public ArnoldResponse<List<CommentResponse>> list(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Positive @QueryParam("project_id") final long projectId,
            @Positive @QueryParam("mr_iid") final long mrIid) {
        return ArnoldResponse.ok(commentService.list(projectId, mrIid, AuthenticatedUser.id(user)));
    }

    @POST
    @PermitAll
    @Operation(description = "Create a GitLab discussion")
    public ArnoldResponse<CommentResponse> create(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Valid final CreateDiscussionRequest request) {
        return ArnoldResponse.ok(commentService.create(request, AuthenticatedUser.id(user)));
    }
}
