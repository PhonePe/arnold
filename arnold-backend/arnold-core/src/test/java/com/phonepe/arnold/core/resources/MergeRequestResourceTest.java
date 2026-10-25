package com.phonepe.arnold.core.resources;

import com.phonepe.arnold.core.exception.ViolationExceptionMapper;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.ChangeRequestService;
import com.phonepe.arnold.core.services.UserMergeRequestService;
import com.phonepe.arnold.core.services.MergeQueueService;
import com.phonepe.arnold.models.mergequeue.ReorderMergeQueueRequest;
import com.phonepe.arnold.models.mergequeue.EnrichedMergeQueueResponse;
import com.phonepe.arnold.models.mergerequest.UserMergeRequestPageResponse;
import com.phonepe.arnold.models.ArnoldResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.arnold.models.mergerequest.ResolveMergeRequestRequest;
import com.phonepe.arnold.models.mergerequest.SmartDiffResponse;
import com.phonepe.arnold.core.auth.ArnoldPrincipal;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import io.dropwizard.auth.AuthValueFactoryProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.Entity;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import java.security.Principal;
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
    private static final UserMergeRequestService USER_SERVICE = mock(UserMergeRequestService.class);
    private static final MergeQueueService QUEUE_SERVICE = mock(MergeQueueService.class);
    private static final ArnoldPrincipal USER = user();
    private static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addResource(new MergeRequestResource(SERVICE, USER_SERVICE))
            .addResource(new EnrichedMergeQueueResource(QUEUE_SERVICE))
            .addProvider(new TestSecurityContextFilter())
            .addProvider(new AuthValueFactoryProvider.Binder<>(ArnoldPrincipal.class))
            .addProvider(new ViolationExceptionMapper())
            .addProvider(RolesAllowedDynamicFeature.class)
            .build();

    @AfterEach
    void tearDown() {
        reset(SERVICE, USER_SERVICE, QUEUE_SERVICE);
    }

    @Test
    void testMine_shouldUseAuthenticatedUserAndOpenDefault_whenStateIsOmitted() {
        when(USER_SERVICE.list("user-1", "all", 50, null, "open"))
                .thenReturn(new UserMergeRequestPageResponse(List.of(), null, java.util.Map.of()));
        final var response = RESOURCES.target("/merge-requests/v1/mine")
                .queryParam("userId", "other-user").request(MediaType.APPLICATION_JSON_TYPE).get();
        assertEquals(200, response.getStatus());
        verify(USER_SERVICE).list("user-1", "all", 50, null, "open");
    }

    @Test
    void testMine_shouldForwardMergedSelector_whenStateIsMerged() {
        when(USER_SERVICE.list("user-1", "all", 50, null, "merged"))
                .thenReturn(new UserMergeRequestPageResponse(List.of(), null, java.util.Map.of()));
        final var response = RESOURCES.target("/merge-requests/v1/mine")
                .queryParam("state", "merged").request(MediaType.APPLICATION_JSON_TYPE).get();
        assertEquals(200, response.getStatus());
        verify(USER_SERVICE).list("user-1", "all", 50, null, "merged");
    }

    @Test
    void testPriority_shouldSaveForAuthenticatedUser_whenPayloadIsValid() {
        final var request = ReorderMergeQueueRequest.builder()
                .orderedItemIds(List.of("gitlab:10:2", "gitlab:10:1")).version(3L).build();
        when(QUEUE_SERVICE.reorderPriority("user-1", request))
                .thenReturn(new EnrichedMergeQueueResponse(List.of(), 0, 4, 0, request.getOrderedItemIds()));
        final var response = RESOURCES.target("/merge-queues/v2/mine/priority")
                .queryParam("userId", "other-user").request(MediaType.APPLICATION_JSON_TYPE).put(Entity.json(request));
        assertEquals(200, response.getStatus());
        verify(QUEUE_SERVICE).reorderPriority("user-1", request);
    }

    @Test
    void testPriority_shouldRejectEmptyOrder_whenPayloadIsInvalid() {
        final var response = RESOURCES.target("/merge-queues/v2/mine/priority")
                .request(MediaType.APPLICATION_JSON_TYPE).put(Entity.json("{\"orderedItemIds\":[],\"version\":0}"));
        assertEquals(422, response.getStatus());
        verifyNoInteractions(QUEUE_SERVICE);
    }

    @Nested
    class Resolve {
        @Test
        void testResolve_shouldReturnMergeRequest_whenUrlIsValid() {
            final var url = "https://gitlab.example/team/repo/-/merge_requests/12";
            final var mergeRequest = mergeRequest();
            when(SERVICE.resolve(VersionControlProviderType.GITLAB, url, "user-1")).thenReturn(mergeRequest);

            final var body = new MergeRequestResource(SERVICE).resolve(USER,
                    ResolveMergeRequestRequest.builder().url(url).build());

            assertEquals(mergeRequest, body.getResponse());
            verify(SERVICE).resolve(VersionControlProviderType.GITLAB, url, "user-1");
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
        when(SERVICE.get(VersionControlProviderType.GITLAB, "10", 12L, "user-1")).thenReturn(mergeRequest);

        final var response = new MergeRequestResource(SERVICE).get(USER, "GITLAB", 10L, 12L);

        assertEquals(mergeRequest, response.getResponse());
        verify(SERVICE).get(VersionControlProviderType.GITLAB, "10", 12L, "user-1");
    }

    @Test
    void testGetDiff_shouldReturnRawDiff_whenIdentifiersAreValid() {
        final var diff = "diff --git a/example.txt b/example.txt";
        when(SERVICE.getDiff(VersionControlProviderType.GITLAB, 10L, 12L, "user-1")).thenReturn(diff);

        final var response = new MergeRequestResource(SERVICE).getDiff(USER, "GITLAB", 10L, 12L);

        assertEquals(diff, response.getResponse());
        verify(SERVICE).getDiff(VersionControlProviderType.GITLAB, 10L, 12L, "user-1");
    }

    @Nested
    class SmartDiff {
        private final String headSha = "a".repeat(40);
        private final SmartDiffResponse notRequested = new SmartDiffResponse(headSha, "not_requested", List.of(),
                0, null);

        @Test
        void testGetSmartDiff_shouldReturnStoredContract_whenShaIsValid() {
            when(SERVICE.getSmartDiff(VersionControlProviderType.GITLAB, 10L, 12L, headSha)).thenReturn(notRequested);

            final var response = RESOURCES.target("/merge-requests/v1/10/12/smart-diff")
                    .queryParam("headSha", headSha).request(MediaType.APPLICATION_JSON_TYPE).get();
            final ArnoldResponse<SmartDiffResponse> body = response.readEntity(new GenericType<>() { });

            assertEquals(200, response.getStatus());
            assertEquals(notRequested, body.getResponse());
        }

        @Test
        void testGetSmartDiff_shouldRejectRequest_whenShaIsMalformed() {
            final var response = RESOURCES.target("/merge-requests/v1/10/12/smart-diff")
                    .queryParam("headSha", "abc").request(MediaType.APPLICATION_JSON_TYPE).get();

            assertEquals(400, response.getStatus());
            verifyNoInteractions(SERVICE);
        }

        @Test
        void testRequestSmartDiff_shouldQueueRequest_whenPayloadIsValid() {
            when(SERVICE.requestSmartDiff(org.mockito.ArgumentMatchers.eq(VersionControlProviderType.GITLAB), org.mockito.ArgumentMatchers.eq(10L),
                    org.mockito.ArgumentMatchers.eq(12L), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq("user-1")))
                    .thenReturn(new SmartDiffResponse(headSha, "queued", List.of(), 123L, null));

            final var response = RESOURCES.target("/merge-requests/v1/10/12/smart-diff")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.json("{\"headSha\":\"" + headSha + "\"}"));

            assertEquals(200, response.getStatus());
        }

        @Test
        void testUpdateSmartDiff_shouldAcceptCompletedNestedContract_whenAuthorized() {
            when(SERVICE.updateSmartDiff(org.mockito.ArgumentMatchers.eq(VersionControlProviderType.GITLAB), org.mockito.ArgumentMatchers.eq(10L),
                    org.mockito.ArgumentMatchers.eq(12L), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(new SmartDiffResponse(headSha, "completed", List.of(), 123L, null));
            final var payload = """
                    {"executionId":"123e4567-e89b-42d3-a456-426614174000","headSha":"%s",
                    "status":"completed","summary":"Review the contract first.","groups":[{"id":"g1","title":"Contract",
                    "rationale":"Start here","steps":[{"id":"s1","title":"API","explanation":"Review it",
                    "changes":[{"filePath":"src/A.java","oldStartLine":10,"oldEndLine":14,
                    "newStartLine":12,"newEndLine":18}]}]}],
                    "errorMessage":null}
                    """.formatted(headSha);

            final var response = RESOURCES.target("/merge-requests/v1/10/12/smart-diff")
                    .request(MediaType.APPLICATION_JSON_TYPE).header("X-Test-Role", "allowed")
                    .put(Entity.json(payload));

            assertEquals(200, response.getStatus());
        }

        @Test
        void testUpdateSmartDiff_shouldRejectRequest_whenLineRangeIsIncomplete() {
            final var payload = """
                    {"executionId":"123e4567-e89b-42d3-a456-426614174000","headSha":"%s",
                    "status":"completed","summary":"Overview","groups":[{"id":"g1","title":"Contract",
                    "rationale":"Start here","steps":[{"id":"s1","title":"API","explanation":"Review it",
                    "changes":[{"filePath":"src/A.java","oldStartLine":null,"oldEndLine":null,
                    "newStartLine":12,"newEndLine":null}]}]}]}
                    """.formatted(headSha);

            final var response = RESOURCES.target("/merge-requests/v1/10/12/smart-diff")
                    .request(MediaType.APPLICATION_JSON_TYPE).header("X-Test-Role", "allowed")
                    .put(Entity.json(payload));

            assertEquals(422, response.getStatus());
            verifyNoInteractions(SERVICE);
        }

        @Test
        void testUpdateSmartDiff_shouldRejectRequest_whenFailedStatusHasNoError() {
            final var payload = ("{\"executionId\":\"123e4567-e89b-42d3-a456-426614174000\","
                    + "\"headSha\":\"%s\",\"status\":\"failed\",\"groups\":[]}").formatted(headSha);

            final var response = RESOURCES.target("/merge-requests/v1/10/12/smart-diff")
                    .request(MediaType.APPLICATION_JSON_TYPE).header("X-Test-Role", "allowed")
                    .put(Entity.json(payload));

            assertEquals(422, response.getStatus());
            verifyNoInteractions(SERVICE);
        }

        @Test
        void testUpdateSmartDiff_shouldRejectRequest_whenCompletedSummaryIsBlank() {
            final var payload = """
                    {"executionId":"123e4567-e89b-42d3-a456-426614174000","headSha":"%s",
                    "status":"completed","summary":" ","groups":[{"id":"g1","title":"Contract",
                    "rationale":"Start here","steps":[{"id":"s1","title":"API","explanation":"Review it",
                    "changes":[{"filePath":"src/A.java","oldStartLine":10,"oldEndLine":14,
                    "newStartLine":12,"newEndLine":18}]}]}]}
                    """.formatted(headSha);

            final var response = RESOURCES.target("/merge-requests/v1/10/12/smart-diff")
                    .request(MediaType.APPLICATION_JSON_TYPE).header("X-Test-Role", "allowed")
                    .put(Entity.json(payload));

            assertEquals(422, response.getStatus());
            verifyNoInteractions(SERVICE);
        }

        @Test
        void testUpdateSmartDiff_shouldRejectRequest_whenExecutionIdIsMalformed() {
            final var payload = ("{\"executionId\":\"old\",\"headSha\":\"%s\",\"status\":\"running\","
                    + "\"groups\":[]}").formatted(headSha);

            final var response = RESOURCES.target("/merge-requests/v1/10/12/smart-diff")
                    .request(MediaType.APPLICATION_JSON_TYPE).header("X-Test-Role", "allowed")
                    .put(Entity.json(payload));

            assertEquals(422, response.getStatus());
            verifyNoInteractions(SERVICE);
        }

        @Test
        void testUpdateSmartDiff_shouldReturnForbidden_whenRoleIsMissing() {
            final var payload = ("{\"executionId\":\"123e4567-e89b-42d3-a456-426614174000\","
                    + "\"headSha\":\"%s\",\"status\":\"running\",\"groups\":[]}").formatted(headSha);

            final var response = RESOURCES.target("/merge-requests/v1/10/12/smart-diff")
                    .request(MediaType.APPLICATION_JSON_TYPE).put(Entity.json(payload));

            assertEquals(403, response.getStatus());
            verifyNoInteractions(SERVICE);
        }
    }

    private static MergeRequestResponse mergeRequest() {
        return new MergeRequestResponse("gitlab:10:12", "GITLAB", "10", "100", 12L, "team/repo", "Title", null,
                "https://gitlab.example/team/repo/-/merge_requests/12", "feature", "main", "abc", "opened",
                "can_be_merged", false, 1L, "Alice", List.of(2L), List.of("Bob"), List.of(3L),
                List.of("Carol"), List.of("backend"), 0, 0, 0, false, true, 1000L, 1100L);
    }

    private static ArnoldPrincipal user() {
        return ArnoldPrincipal.builder()
                .id("user-1")
                .type(ArnoldPrincipal.Type.USER)
                .displayName("User One")
                .email("user@example.com")
                .build();
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
                    return "allowed".equals(requestContext.getHeaderString("X-Test-Role"));
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
