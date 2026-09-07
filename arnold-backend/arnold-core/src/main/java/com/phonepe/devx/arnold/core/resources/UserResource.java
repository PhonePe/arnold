package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.auth.AuthenticatedUser;
import com.phonepe.devx.arnold.core.common.Constants;
import com.phonepe.devx.arnold.core.services.UserService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.user.UserResponse;
import com.phonepe.olympus.im.models.security.ServiceUserPrincipal;
import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/users/v1")
@Tag(name = "User APIs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = Constants.OLYMPUS)
@SecurityScheme(name = Constants.OLYMPUS, type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = Constants.AUTHORIZATION_HEADER)
@AllArgsConstructor(onConstructor_ = @Inject)
public class UserResource {
    private final UserService userService;

    @GET
    @PermitAll
    public ArnoldResponse<List<UserResponse>> listUsers() {
        return ArnoldResponse.ok(userService.list());
    }

    @GET
    @Path("/me")
    @PermitAll
    public ArnoldResponse<UserResponse> getCurrentUser(@Parameter(hidden = true) @Auth ServiceUserPrincipal user) {
        return ArnoldResponse.ok(userService.getOrCreateCurrentUser(AuthenticatedUser.details(user)));
    }

    @DELETE
    @Path("/me")
    @PermitAll
    public ArnoldResponse<String> deleteCurrentUser(@Parameter(hidden = true) @Auth ServiceUserPrincipal user) {
        userService.deleteUser(AuthenticatedUser.id(user));
        return ArnoldResponse.ok("deleted");
    }
}
