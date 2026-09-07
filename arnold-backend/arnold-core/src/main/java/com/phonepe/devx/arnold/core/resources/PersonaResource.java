package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.auth.AuthenticatedUser;
import com.phonepe.devx.arnold.core.services.PersonaService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.persona.CreatePersonaRequest;
import com.phonepe.devx.arnold.models.persona.PersonaResponse;
import com.phonepe.devx.arnold.models.persona.UpdatePersonaRequest;
import com.phonepe.olympus.im.models.security.ServiceUserPrincipal;
import io.dropwizard.auth.Auth;
import lombok.AllArgsConstructor;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/personas/v1")
@Produces(MediaType.APPLICATION_JSON)
@AllArgsConstructor(onConstructor_ = @Inject)
public class PersonaResource {
    private final PersonaService service;

    @GET
    @PermitAll
    public ArnoldResponse<List<PersonaResponse>> list(@Auth final ServiceUserPrincipal user) {
        return ArnoldResponse.ok(service.list(AuthenticatedUser.id(user)));
    }

    @GET
    @Path("/{personaId}")
    @PermitAll
    public ArnoldResponse<PersonaResponse> get(@Auth final ServiceUserPrincipal user,
                                                @PathParam("personaId") final String personaId) {
        return ArnoldResponse.ok(service.get(AuthenticatedUser.id(user), personaId));
    }

    @POST
    @PermitAll
    public ArnoldResponse<PersonaResponse> create(@Auth final ServiceUserPrincipal user,
                                                   @Valid final CreatePersonaRequest request) {
        return ArnoldResponse.ok(service.create(AuthenticatedUser.id(user), request));
    }

    @PUT
    @Path("/{personaId}")
    @PermitAll
    public ArnoldResponse<PersonaResponse> update(@Auth final ServiceUserPrincipal user,
                                                   @PathParam("personaId") final String personaId,
                                                   @Valid final UpdatePersonaRequest request) {
        return ArnoldResponse.ok(service.update(AuthenticatedUser.id(user), personaId, request));
    }

    @POST
    @Path("/{personaId}/publish")
    @PermitAll
    public ArnoldResponse<PersonaResponse> publish(@Auth final ServiceUserPrincipal user,
                                                    @PathParam("personaId") final String personaId,
                                                    @Positive @QueryParam("version") final long version) {
        return ArnoldResponse.ok(service.publish(AuthenticatedUser.id(user), personaId, version));
    }

    @POST
    @Path("/{personaId}/unpublish")
    @PermitAll
    public ArnoldResponse<PersonaResponse> unpublish(@Auth final ServiceUserPrincipal user,
                                                      @PathParam("personaId") final String personaId,
                                                      @Positive @QueryParam("version") final long version) {
        return ArnoldResponse.ok(service.unpublish(AuthenticatedUser.id(user), personaId, version));
    }

    @DELETE
    @Path("/{personaId}")
    @PermitAll
    public ArnoldResponse<Boolean> delete(@Auth final ServiceUserPrincipal user,
                                           @PathParam("personaId") final String personaId,
                                           @Positive @QueryParam("version") final long version) {
        service.delete(AuthenticatedUser.id(user), personaId, version);
        return ArnoldResponse.ok(true);
    }
}
