package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.services.UserSyncControlService;
import com.phonepe.devx.arnold.core.services.DashboardService;
import com.phonepe.devx.arnold.core.model.sync.UserSyncControlState;
import com.phonepe.devx.arnold.core.model.sync.UserSyncMode;
import com.phonepe.olympus.im.models.security.ServiceUserPrincipal;
import com.phonepe.olympus.im.models.user.HumanUserDetails;
import com.phonepe.olympus.im.models.user.UserAuthDetails;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.Entity;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.SecurityContext;
import java.security.Principal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(DropwizardExtensionsSupport.class)
class AdminUserSyncResourceTest {
    private static final UserSyncControlService SERVICE = mock(UserSyncControlService.class);
    private static final ServiceUserPrincipal USER = user();
    private static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addResource(new AdminUserSyncResource(SERVICE))
            .addResource(new DashboardResource(mock(DashboardService.class)))
            .addProvider(new TestSecurityContextFilter())
            .addProvider(new AuthValueFactoryProvider.Binder<>(ServiceUserPrincipal.class))
            .addProvider(RolesAllowedDynamicFeature.class)
            .build();

    @Test
    void testAdminEndpoints_shouldRequireSuperadmin_whenAuthenticated() {
        final var state = new UserSyncControlState(UserSyncMode.AUTO, UserSyncMode.AUTO,
                false, 0, true, null, 0, false, "node");
        when(SERVICE.status()).thenReturn(state);
        when(SERVICE.setMode(UserSyncMode.AUTO)).thenReturn(state);
        when(SERVICE.resetCallbackObservation()).thenReturn(state);
        for (final var role : new String[]{"user", "admin", "superadmin"}) {
            clearInvocations(SERVICE);
            final var expected = role.equals("superadmin") ? 200 : 403;
            try (final var response = RESOURCES.target("/admin/v1/user-sync").request()
                    .header("X-Test-Role", role).get()) {
                assertEquals(expected, response.getStatus());
            }
            try (final var response = RESOURCES.target("/admin/v1/user-sync").request()
                    .header("X-Test-Role", role).put(Entity.json("{\"mode\":\"AUTO\"}"))) {
                assertEquals(expected, response.getStatus());
            }
            try (final var response = RESOURCES.target("/admin/v1/user-sync/callback-observation/reset").request()
                    .header("X-Test-Role", role).post(Entity.json("{}"))) {
                assertEquals(expected, response.getStatus());
            }
            if (expected == 403) verifyNoInteractions(SERVICE);
            else {
                verify(SERVICE).status();
                verify(SERVICE).setMode(UserSyncMode.AUTO);
                verify(SERVICE).resetCallbackObservation();
            }
            try (final var response = RESOURCES.target("/app/v1/permissions").request()
                    .header("X-Test-Role", role).get()) {
                assertEquals(expected, response.getStatus());
                if (expected == 200) assertTrue(response.readEntity(String.class).contains("\"superadmin\":true"));
            }
        }
    }

    private static ServiceUserPrincipal user() {
        final var details = new HumanUserDetails();
        details.setUserId("user-1");
        details.setName("User One");
        details.setEmail("user@example.com");
        return new ServiceUserPrincipal(UserAuthDetails.builder().userDetails(details).build());
    }

    @Priority(Priorities.AUTHENTICATION)
    private static class TestSecurityContextFilter implements ContainerRequestFilter {
        @Override
        public void filter(final ContainerRequestContext requestContext) {
            requestContext.setSecurityContext(new SecurityContext() {
                @Override
                public Principal getUserPrincipal() {
                    return USER;
                }

                @Override
                public boolean isUserInRole(final String role) {
                    return role.equals(requestContext.getHeaderString("X-Test-Role"));
                }

                @Override
                public boolean isSecure() {
                    return false;
                }

                @Override
                public String getAuthenticationScheme() {
                    return "TEST";
                }
            });
        }
    }
}
