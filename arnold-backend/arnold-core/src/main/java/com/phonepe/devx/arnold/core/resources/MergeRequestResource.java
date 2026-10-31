package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.auth.AuthenticatedUser;
import com.phonepe.devx.arnold.core.common.Constants;
import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.services.ChangeRequestService;
import com.phonepe.devx.arnold.core.services.UserMergeRequestService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.devx.arnold.models.mergerequest.AddReviewerRequest;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestFilesResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestDiffVersionResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestReviewResponse;
import com.phonepe.devx.arnold.models.mergerequest.ReviewerCandidateResponse;
import com.phonepe.devx.arnold.models.mergerequest.ViewedFilesRequest;
import com.phonepe.devx.arnold.models.mergerequest.ViewedFilesResponse;
import com.phonepe.devx.arnold.models.mergerequest.ResolveMergeRequestRequest;
import com.phonepe.devx.arnold.models.mergerequest.UserMergeRequestPageResponse;
import com.phonepe.olympus.im.models.security.ServiceUserPrincipal;
import com.phonepe.devx.arnold.models.mergerequest.SmartDiffRequest;
import com.phonepe.devx.arnold.models.mergerequest.SmartDiffResponse;
import com.phonepe.devx.arnold.models.mergerequest.SmartDiffResultRequest;
import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.Locale;
import java.util.List;

@Path("/merge-requests/v1")
@Tag(name = "Merge Request APIs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = Constants.OLYMPUS)
@SecurityScheme(name = Constants.OLYMPUS, type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = Constants.AUTHORIZATION_HEADER)
public class MergeRequestResource {
    private final ChangeRequestService mergeRequestService;
    private final UserMergeRequestService userMergeRequestService;

    @Inject
    public MergeRequestResource(final ChangeRequestService mergeRequestService,
                                final UserMergeRequestService userMergeRequestService) {
        this.mergeRequestService = mergeRequestService;
        this.userMergeRequestService = userMergeRequestService;
    }

    public MergeRequestResource(final ChangeRequestService mergeRequestService) {
        this(mergeRequestService, null);
    }

    @GET
    @Path("/mine")
    @PermitAll
    @Operation(description = "List the authenticated user's synced open or recently merged (last 30 days) merge requests")
    public ArnoldResponse<UserMergeRequestPageResponse> mine(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @QueryParam("role") @DefaultValue("all") final String role,
            @QueryParam("limit") @DefaultValue("50") final int limit,
            @QueryParam("cursor") final String cursor,
            @QueryParam("state") @DefaultValue("open") final String state) {
        return ArnoldResponse.ok(userMergeRequestService.list(AuthenticatedUser.id(user), role, limit, cursor, state));
    }

    @POST
    @Path("/resolve")
    @PermitAll
    @Operation(description = "Resolve a GitLab merge request URL")
    public ArnoldResponse<MergeRequestResponse> resolve(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Valid final ResolveMergeRequestRequest request) {
        return ArnoldResponse.ok(mergeRequestService.resolve(provider(request.getProvider()), request.getUrl(),
                AuthenticatedUser.id(user)));
    }

    @GET
    @Path("/{projectId}/{iid}")
    @PermitAll
    @Operation(description = "Get a merge request")
    public ArnoldResponse<MergeRequestResponse> get(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @QueryParam("provider") @DefaultValue("GITLAB") final String provider,
            @Positive @PathParam("projectId") final long projectId,
            @Positive @PathParam("iid") final long iid) {
        return ArnoldResponse.ok(mergeRequestService.get(provider(provider), Long.toString(projectId), iid,
                AuthenticatedUser.id(user)));
    }

    @POST
    @Path("/{projectId}/{iid}/reviewers")
    @PermitAll
    @Operation(description = "Add a GitLab merge request reviewer")
    public ArnoldResponse<MergeRequestResponse> addReviewer(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Positive @PathParam("projectId") final long projectId,
            @Positive @PathParam("iid") final long iid,
            @Valid final AddReviewerRequest request) {
        return ArnoldResponse.ok(mergeRequestService.addReviewer(projectId, iid, request.getUsername(),
                AuthenticatedUser.id(user)));
    }

    @DELETE
    @Path("/{projectId}/{iid}/reviewers/{reviewerId}")
    @PermitAll
    @Operation(description = "Remove a GitLab merge request reviewer")
    public ArnoldResponse<MergeRequestResponse> removeReviewer(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Positive @PathParam("projectId") final long projectId,
            @Positive @PathParam("iid") final long iid,
            @Positive @PathParam("reviewerId") final long reviewerId) {
        return ArnoldResponse.ok(mergeRequestService.removeReviewer(projectId, iid, reviewerId,
                AuthenticatedUser.id(user)));
    }

    @GET
    @Path("/{projectId}/reviewer-candidates")
    @PermitAll
    @Operation(description = "Search eligible GitLab reviewers")
    public ArnoldResponse<List<ReviewerCandidateResponse>> searchReviewers(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Positive @PathParam("projectId") final long projectId,
            @NotBlank @Size(max = 100) @QueryParam("query") final String query) {
        return ArnoldResponse.ok(mergeRequestService.searchReviewers(projectId, query, AuthenticatedUser.id(user)));
    }

    private VersionControlProviderType provider(final String value) {
        try {
            return VersionControlProviderType.valueOf((value == null || value.isBlank() ? "GITLAB" : value)
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw ArnoldError.raise(ArnoldErrorCode.WRONG_INPUT_ERROR)
                    .message("Unsupported version control provider").build();
        }
    }

    @GET
    @Path("/{projectId}/{iid}/review-status")
    @PermitAll
    @Operation(description = "Get live merge checks and reviewer status")
    public ArnoldResponse<MergeRequestReviewResponse> getReview(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Positive @PathParam("projectId") final long projectId,
            @Positive @PathParam("iid") final long iid) {
        return ArnoldResponse.ok(mergeRequestService.getReview(projectId, iid, AuthenticatedUser.id(user)));
    }

    @GET
    @Path("/{projectId}/{iid}/diff")
    @PermitAll
    @Operation(description = "Get the latest merge request diff")
    public ArnoldResponse<String> getDiff(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Positive @PathParam("projectId") final long projectId,
            @Positive @PathParam("iid") final long iid) {
        return ArnoldResponse.ok(mergeRequestService.getDiff(projectId, iid, AuthenticatedUser.id(user)));
    }

    @GET
    @Path("/{projectId}/{iid}/diff-versions")
    @PermitAll
    @Operation(description = "List merge request diff versions")
    public ArnoldResponse<List<MergeRequestDiffVersionResponse>> listDiffVersions(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Positive @PathParam("projectId") final long projectId,
            @Positive @PathParam("iid") final long iid) {
        return ArnoldResponse.ok(mergeRequestService.listDiffVersions(projectId, iid, AuthenticatedUser.id(user)));
    }

    @GET
    @Path("/{projectId}/{iid}/diff-versions/{versionId}")
    @PermitAll
    @Operation(description = "Get a merge request diff version")
    public ArnoldResponse<String> getDiffVersion(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Positive @PathParam("projectId") final long projectId,
            @Positive @PathParam("iid") final long iid,
            @Positive @PathParam("versionId") final long versionId) {
        return ArnoldResponse.ok(mergeRequestService.getDiffVersion(projectId, iid, versionId,
                AuthenticatedUser.id(user)));
    }

    @GET
    @Path("/{projectId}/{iid}/files")
    @PermitAll
    @Operation(description = "Get both versions of a changed merge request file")
    public ArnoldResponse<MergeRequestFilesResponse> getFiles(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Positive @PathParam("projectId") final long projectId,
            @Positive @PathParam("iid") final long iid,
            @NotBlank @QueryParam("oldPath") final String oldPath,
            @NotBlank @QueryParam("newPath") final String newPath,
            @Pattern(regexp = "[0-9a-fA-F]{40,64}") @QueryParam("baseSha") final String baseSha,
            @Pattern(regexp = "[0-9a-fA-F]{40,64}") @QueryParam("headSha") final String headSha) {
        return ArnoldResponse.ok(mergeRequestService.getFiles(projectId, iid, oldPath, newPath, baseSha, headSha,
                AuthenticatedUser.id(user)));
    }

    @GET
    @Path("/{projectId}/{iid}/viewed-files")
    @PermitAll
    @Operation(description = "Get the current user's viewed files")
    public ArnoldResponse<ViewedFilesResponse> getViewedFiles(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Positive @PathParam("projectId") final long projectId,
            @Positive @PathParam("iid") final long iid,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{40,64}") @QueryParam("headSha") final String headSha) {
        return ArnoldResponse.ok(mergeRequestService.getViewedFiles(projectId, iid, headSha,
                AuthenticatedUser.id(user)));
    }

    @PUT
    @Path("/{projectId}/{iid}/viewed-files")
    @PermitAll
    @Operation(description = "Replace the current user's viewed files")
    public ArnoldResponse<ViewedFilesResponse> updateViewedFiles(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Positive @PathParam("projectId") final long projectId,
            @Positive @PathParam("iid") final long iid,
            @Valid final ViewedFilesRequest request) {
        return ArnoldResponse.ok(mergeRequestService.updateViewedFiles(projectId, iid, request,
                AuthenticatedUser.id(user)));
    }

    @GET
    @Path("/{projectId}/{iid}/smart-diff")
    @PermitAll
    @Operation(description = "Get Smart Diff organization for a merge request head")
    public ArnoldResponse<SmartDiffResponse> getSmartDiff(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Positive @PathParam("projectId") final long projectId,
            @Positive @PathParam("iid") final long iid,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{40,64}") @QueryParam("headSha") final String headSha) {
        return ArnoldResponse.ok(mergeRequestService.getSmartDiff(projectId, iid, headSha));
    }

    @POST
    @Path("/{projectId}/{iid}/smart-diff")
    @PermitAll
    @Operation(description = "Queue Smart Diff organization for a merge request head")
    public ArnoldResponse<SmartDiffResponse> requestSmartDiff(
            @Parameter(hidden = true) @Auth final ServiceUserPrincipal user,
            @Positive @PathParam("projectId") final long projectId,
            @Positive @PathParam("iid") final long iid,
            @Valid final SmartDiffRequest request) {
        return ArnoldResponse.ok(mergeRequestService.requestSmartDiff(projectId, iid, request,
                AuthenticatedUser.id(user)));
    }

    @PUT
    @Path("/{projectId}/{iid}/smart-diff")
    @RolesAllowed("smart_diff_callback_write")
    @Operation(description = "Save a Smart Diff producer lifecycle result")
    public ArnoldResponse<SmartDiffResponse> updateSmartDiff(
            @Positive @PathParam("projectId") final long projectId,
            @Positive @PathParam("iid") final long iid,
            @Valid final SmartDiffResultRequest request) {
        return ArnoldResponse.ok(mergeRequestService.updateSmartDiff(projectId, iid, request));
    }
}
