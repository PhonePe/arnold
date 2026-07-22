package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.services.ChangeRequestService;
import com.phonepe.devx.arnold.models.ArnoldResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.devx.arnold.models.mergerequest.ResolveMergeRequestRequest;
import com.phonepe.olympus.im.client.security.ServiceUserPrincipal;
import com.phonepe.olympus.im.models.user.HumanUserDetails;
import com.phonepe.olympus.im.models.user.UserAuthDetails;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import io.dropwizard.auth.AuthValueFactoryProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(DropwizardExtensionsSupport.class)
class MergeRequestResourceTest {
    private static final ChangeRequestService SERVICE = mock(ChangeRequestService.class);
    private static final ServiceUserPrincipal USER = user();
    private static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addResource(new MergeRequestResource(SERVICE))
            .addProvider(new AuthValueFactoryProvider.Binder<>(ServiceUserPrincipal.class))
            .build();

    @AfterEach
    void tearDown() {
        reset(SERVICE);
    }

    @Nested
    class Resolve {
        @Test
        void testResolve_shouldReturnMergeRequest_whenUrlIsValid() {
            final var url = "https://gitlab.example/team/repo/-/merge_requests/12";
            final var mergeRequest = mergeRequest();
            when(SERVICE.resolve(url, "user-1")).thenReturn(mergeRequest);

            final var body = new MergeRequestResource(SERVICE).resolve(USER,
                    ResolveMergeRequestRequest.builder().url(url).build());

            assertEquals(mergeRequest, body.getResponse());
            verify(SERVICE).resolve(url, "user-1");
        }

        @Test
        void testResolve_shouldRejectRequest_whenUrlIsBlank() {
            final var response = RESOURCES.target("/merge-requests/v1/resolve")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.json(ResolveMergeRequestRequest.builder().url(" ").build()));

            assertEquals(422, response.getStatus());
            verifyNoInteractions(SERVICE);
        }
    }

    @Test
    void testGet_shouldReturnMergeRequest_whenIdentifiersAreValid() {
        final var mergeRequest = mergeRequest();
        when(SERVICE.get(10L, 12L, "user-1")).thenReturn(mergeRequest);

        final var response = new MergeRequestResource(SERVICE).get(USER, 10L, 12L);

        assertEquals(mergeRequest, response.getResponse());
        verify(SERVICE).get(10L, 12L, "user-1");
    }

    private static MergeRequestResponse mergeRequest() {
        return new MergeRequestResponse("10:12", 10L, 100L, 12L, "team/repo", "Title", null,
                "https://gitlab.example/team/repo/-/merge_requests/12", "feature", "main", "abc", "opened",
                "can_be_merged", false, 1L, List.of(2L), List.of(3L), List.of("backend"), 1000L, 1100L);
    }

    private static ServiceUserPrincipal user() {
        final var details = new HumanUserDetails();
        details.setUserId("user-1");
        details.setName("User One");
        details.setEmail("user@example.com");
        return new ServiceUserPrincipal(UserAuthDetails.builder().userDetails(details).build());
    }
}
