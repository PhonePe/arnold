package com.phonepe.arnold.core.resources;

import com.phonepe.arnold.core.auth.AuthenticatedUser;
import com.phonepe.arnold.core.services.MergeQueueService;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.mergequeue.EnrichedMergeQueueResponse;
import com.phonepe.arnold.core.auth.ArnoldPrincipal;
import io.dropwizard.auth.Auth;
import lombok.AllArgsConstructor;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.GET;
import com.phonepe.arnold.models.mergequeue.ReorderMergeQueueRequest;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/merge-queues/v2")
@Produces(MediaType.APPLICATION_JSON)
@AllArgsConstructor(onConstructor_ = @Inject)
public class EnrichedMergeQueueResource {
    private final MergeQueueService service;

    @GET
    @Path("/mine")
    @PermitAll
    public ArnoldResponse<EnrichedMergeQueueResponse> get(@Auth final ArnoldPrincipal user) {
        return ArnoldResponse.ok(service.getEnriched(AuthenticatedUser.id(user)));
    }
    @PUT
    @Path("/mine/priority")
    @Consumes(MediaType.APPLICATION_JSON)
    @PermitAll
    public ArnoldResponse<EnrichedMergeQueueResponse> reorderPriority(
            @Auth final ArnoldPrincipal user, @Valid final ReorderMergeQueueRequest request) {
        return ArnoldResponse.ok(service.reorderPriority(AuthenticatedUser.id(user), request));
    }
}
