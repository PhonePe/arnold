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

import com.phonepe.arnold.core.services.GroupAnalysisService;
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
import static org.mockito.Mockito.*;

@ExtendWith(DropwizardExtensionsSupport.class)
class GroupAnalysisCallbackResourceTest {
    private static final GroupAnalysisService SERVICE = mock(GroupAnalysisService.class);
    private static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addResource(new GroupAnalysisCallbackResource(SERVICE))
            .addProvider(new TestSecurityContextFilter()).addProvider(RolesAllowedDynamicFeature.class).build();

    @Test
    void testCallbacks_shouldRequireServicePermission_whenAuthenticated() {
        for (final var role : new String[]{"user", "superadmin", "analysis_callback_write"}) {
            clearInvocations(SERVICE);
            final var expected = role.equals("analysis_callback_write") ? 200 : 403;
            for (final var suffix : new String[]{"", "/fresh"}) {
                try (final var response = RESOURCES.target("/group-analysis-callbacks/v1/run" + suffix)
                        .queryParam("executionId", "execution").request().header("X-Test-Role", role).get()) {
                    assertEquals(expected, response.getStatus());
                }
            }
            try (final var response = RESOURCES.target("/group-analysis-callbacks/v1/run").request()
                    .header("X-Test-Role", role).put(Entity.json("""
                            {"executionId":"execution","workerId":"worker","sequence":1,"status":"running",
                             "memberStatuses":{},"findings":[]}
                            """))) {
                assertEquals(expected, response.getStatus());
            }
            if (expected == 403) verifyNoInteractions(SERVICE);
            else {
                verify(SERVICE).manifest("run", "execution");
                verify(SERVICE).fresh("run", "execution");
                verify(SERVICE).update(eq("run"), any());
            }
        }
    }

    @Priority(Priorities.AUTHENTICATION)
    private static class TestSecurityContextFilter implements ContainerRequestFilter {
        @Override
        public void filter(final ContainerRequestContext request) {
            request.setSecurityContext(new SecurityContext() {
                public Principal getUserPrincipal() { return () -> "worker"; }
                public boolean isUserInRole(final String role) { return role.equals(request.getHeaderString("X-Test-Role")); }
                public boolean isSecure() { return false; }
                public String getAuthenticationScheme() { return "TEST"; }
            });
        }
    }
}
