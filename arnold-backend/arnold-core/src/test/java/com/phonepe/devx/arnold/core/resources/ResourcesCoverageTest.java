package com.phonepe.devx.arnold.core.resources;

import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.services.ChangeRequestService;
import com.phonepe.devx.arnold.core.services.CommentService;
import com.phonepe.devx.arnold.core.services.GroupAnalysisService;
import com.phonepe.devx.arnold.core.services.PersonaService;
import com.phonepe.devx.arnold.core.services.UserMergeRequestService;
import com.phonepe.devx.arnold.core.services.copilot.CopilotConnectionService;
import com.phonepe.devx.arnold.core.services.copilot.CopilotExecutionService;
import com.phonepe.devx.arnold.core.services.impl.PersonaMetricsService;
import com.phonepe.devx.arnold.core.services.impl.ReviewFeedbackMetricsService;
import com.phonepe.devx.arnold.core.services.impl.TokenUsageMetricsService;
import com.phonepe.devx.arnold.models.analysis.GroupAnalysisResponse;
import com.phonepe.devx.arnold.models.comment.ApplySuggestionRequest;
import com.phonepe.devx.arnold.models.comment.CommentResponse;
import com.phonepe.devx.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.devx.arnold.models.comment.PublishReviewRequest;
import com.phonepe.devx.arnold.models.comment.ResolveDiscussionRequest;
import com.phonepe.devx.arnold.models.comment.UpdateDraftRequest;
import com.phonepe.devx.arnold.models.integration.CopilotAttemptRequest;
import com.phonepe.devx.arnold.models.integration.CopilotConnectionResponse;
import com.phonepe.devx.arnold.models.integration.CopilotDeviceResponse;
import com.phonepe.devx.arnold.models.integration.CopilotExecutionTokenResponse;
import com.phonepe.devx.arnold.models.mergerequest.AddReviewerRequest;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestDiffVersionResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestFilesResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestReviewResponse;
import com.phonepe.devx.arnold.models.mergerequest.ResolveMergeRequestRequest;
import com.phonepe.devx.arnold.models.mergerequest.ReviewerCandidateResponse;
import com.phonepe.devx.arnold.models.mergerequest.SmartDiffRequest;
import com.phonepe.devx.arnold.models.mergerequest.SmartDiffResponse;
import com.phonepe.devx.arnold.models.mergerequest.SmartDiffResultRequest;
import com.phonepe.devx.arnold.models.mergerequest.UserMergeRequestPageResponse;
import com.phonepe.devx.arnold.models.mergerequest.ViewedFilesRequest;
import com.phonepe.devx.arnold.models.mergerequest.ViewedFilesResponse;
import com.phonepe.devx.arnold.models.admin.ReviewFeedbackMetricsResponse;
import com.phonepe.devx.arnold.models.admin.TokenUsageMetricsResponse;
import com.phonepe.devx.arnold.models.persona.CreatePersonaRequest;
import com.phonepe.devx.arnold.models.persona.PersonaMetricsResponse;
import com.phonepe.devx.arnold.models.persona.PersonaMetricsSummaryResponse;
import com.phonepe.devx.arnold.models.persona.PersonaResponse;
import com.phonepe.devx.arnold.models.persona.UpdatePersonaRequest;
import com.phonepe.olympus.im.models.security.ServiceUserPrincipal;
import com.phonepe.olympus.im.models.user.HumanUserDetails;
import com.phonepe.olympus.im.models.user.UserAuthDetails;
import org.junit.jupiter.api.Test;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResourcesCoverageTest {
    private static final String USER_ID = "user-1";
    private static final String SHA = "a".repeat(40);
    private final ServiceUserPrincipal user = principal();

    private static ServiceUserPrincipal principal() {
        final var details = new HumanUserDetails();
        details.setUserId(USER_ID);
        return new ServiceUserPrincipal(UserAuthDetails.builder().userDetails(details).build());
    }

    @Test
    void testMergeRequestResource_shouldDelegateWithAuthenticatedUser_whenCalled() {
        final var service = mock(ChangeRequestService.class);
        final var userService = mock(UserMergeRequestService.class);
        final var resource = new MergeRequestResource(service, userService);
        final var mr = mock(MergeRequestResponse.class);
        final var page = mock(UserMergeRequestPageResponse.class);
        final var review = mock(MergeRequestReviewResponse.class);
        final var files = mock(MergeRequestFilesResponse.class);
        final var viewed = new ViewedFilesResponse(SHA, List.of(), List.of(), Map.of(), 1L);
        final var smart = mock(SmartDiffResponse.class);
        final var candidates = List.<ReviewerCandidateResponse>of();
        final var versions = List.<MergeRequestDiffVersionResponse>of();
        final var viewedRequest = mock(ViewedFilesRequest.class);
        final var smartRequest = mock(SmartDiffRequest.class);
        final var smartResult = mock(SmartDiffResultRequest.class);

        when(userService.list(USER_ID, "all", 50, null, "open")).thenReturn(page);
        when(service.get(any(), org.mockito.ArgumentMatchers.eq("7"), org.mockito.ArgumentMatchers.eq(3L),
                org.mockito.ArgumentMatchers.eq(USER_ID))).thenReturn(mr);
        when(service.addReviewer(7, 3, "bob", USER_ID)).thenReturn(mr);
        when(service.removeReviewer(7, 3, 9, USER_ID)).thenReturn(mr);
        when(service.searchReviewers(7, "bo", USER_ID)).thenReturn(candidates);
        when(service.getReview(7, 3, USER_ID)).thenReturn(review);
        when(service.getDiff(7, 3, USER_ID)).thenReturn("diff");
        when(service.listDiffVersions(7, 3, USER_ID)).thenReturn(versions);
        when(service.getDiffVersion(7, 3, 5, USER_ID)).thenReturn("vdiff");
        when(service.getFiles(7, 3, "o", "n", SHA, SHA, USER_ID)).thenReturn(files);
        when(service.getViewedFiles(7, 3, SHA, USER_ID)).thenReturn(viewed);
        when(service.updateViewedFiles(7, 3, viewedRequest, USER_ID)).thenReturn(viewed);
        when(service.getSmartDiff(7, 3, SHA)).thenReturn(smart);
        when(service.requestSmartDiff(7, 3, smartRequest, USER_ID)).thenReturn(smart);
        when(service.updateSmartDiff(7, 3, smartResult)).thenReturn(smart);

        assertSame(page, resource.mine(user, "all", 50, null, "open").getResponse());
        assertSame(mr, resource.get(user, "gitlab", 7, 3).getResponse());
        assertSame(mr, resource.get(user, null, 7, 3).getResponse());
        assertSame(mr, resource.addReviewer(user, 7, 3, AddReviewerRequest.builder().username("bob").build()).getResponse());
        assertSame(mr, resource.removeReviewer(user, 7, 3, 9).getResponse());
        assertSame(candidates, resource.searchReviewers(user, 7, "bo").getResponse());
        assertSame(review, resource.getReview(user, 7, 3).getResponse());
        assertEquals("diff", resource.getDiff(user, 7, 3).getResponse());
        assertSame(versions, resource.listDiffVersions(user, 7, 3).getResponse());
        assertEquals("vdiff", resource.getDiffVersion(user, 7, 3, 5).getResponse());
        assertSame(files, resource.getFiles(user, 7, 3, "o", "n", SHA, SHA).getResponse());
        assertSame(viewed, resource.getViewedFiles(user, 7, 3, SHA).getResponse());
        assertSame(viewed, resource.updateViewedFiles(user, 7, 3, viewedRequest).getResponse());
        assertSame(smart, resource.getSmartDiff(user, 7, 3, SHA).getResponse());
        assertSame(smart, resource.requestSmartDiff(user, 7, 3, smartRequest).getResponse());
        assertSame(smart, resource.updateSmartDiff(7, 3, smartResult).getResponse());
    }

    @Test
    void testMergeRequestResource_shouldRejectUnknownProvider_whenProviderInvalid() {
        final var resource = new MergeRequestResource(mock(ChangeRequestService.class));

        assertThrows(ArnoldError.class, () -> resource.get(user, "svn", 7, 3));
    }

    @Test
    void testMergeRequestResource_shouldResolveUrl_whenRequestHasProvider() {
        final var service = mock(ChangeRequestService.class);
        final var mr = mock(MergeRequestResponse.class);
        when(service.resolve(any(), org.mockito.ArgumentMatchers.eq("http://x"),
                org.mockito.ArgumentMatchers.eq(USER_ID))).thenReturn(mr);

        final var response = new MergeRequestResource(service).resolve(user,
                ResolveMergeRequestRequest.builder().url("http://x").build());

        assertSame(mr, response.getResponse());
    }

    @Test
    void testDiscussionResource_shouldDelegateWithAuthenticatedUser_whenCalled() {
        final var service = mock(CommentService.class);
        final var resource = new DiscussionResource(service);
        final var comments = List.<CommentResponse>of();
        final var comment = mock(CommentResponse.class);
        final var create = mock(CreateDiscussionRequest.class);
        final var update = mock(UpdateDraftRequest.class);
        final var publish = mock(PublishReviewRequest.class);
        final var resolve = mock(ResolveDiscussionRequest.class);
        when(service.list(7, 3, USER_ID)).thenReturn(comments);
        when(service.listDrafts(7, 3, USER_ID)).thenReturn(comments);
        when(service.create(create, USER_ID)).thenReturn(comment);
        when(service.createDraft(create, USER_ID)).thenReturn(comment);
        when(service.updateDraft(4, update, USER_ID)).thenReturn(comment);
        when(service.publishDrafts(publish, USER_ID)).thenReturn(comments);

        assertSame(comments, resource.list(user, 7, 3).getResponse());
        assertSame(comments, resource.listDrafts(user, 7, 3).getResponse());
        assertSame(comment, resource.create(user, create).getResponse());
        assertSame(comment, resource.createDraft(user, create).getResponse());
        assertSame(comment, resource.updateDraft(user, 4, update).getResponse());
        assertSame(comments, resource.publishDrafts(user, publish).getResponse());
        assertTrue(resource.deleteDraft(user, 4, 7, 3).getResponse());
        assertTrue(resource.resolveDiscussion(user, "d1", resolve).getResponse());
        assertTrue(resource.applySuggestion(user, 8, new ApplySuggestionRequest("msg")).getResponse());
        assertTrue(resource.applySuggestion(user, 9, null).getResponse());
        verify(service).deleteDraft(4, 7, 3, USER_ID);
        verify(service).resolveDiscussion("d1", resolve, USER_ID);
        verify(service).applySuggestion(8, "msg", USER_ID);
        verify(service).applySuggestion(9, null, USER_ID);
    }

    @Test
    void testAdminMetricsResource_shouldForwardFilters_whenCalled() {
        final var feedback = mock(ReviewFeedbackMetricsService.class);
        final var tokens = mock(TokenUsageMetricsService.class);
        final var tokenResponse = mock(TokenUsageMetricsResponse.class);
        final var feedbackResponse = mock(ReviewFeedbackMetricsResponse.class);
        when(tokens.metrics(1L, 2L)).thenReturn(tokenResponse);
        when(feedback.metrics(any(), any(), anyMap())).thenReturn(feedbackResponse);
        final var resource = new AdminMetricsResource(feedback, tokens);

        assertSame(tokenResponse, resource.tokenUsage(user, 1L, 2L).getResponse());
        assertSame(feedbackResponse, resource.reviewFeedback(user, 1L, 2L, "p", "m", "persona", "applied", "high", "bug", "cat", true)
                .getResponse());
        verify(feedback).metrics(1L, 2L, Map.of("project", "p", "model", "m", "personaId", "persona",
                "status", "applied", "priority", "high", "commentType", "bug", "category", "cat",
                "hasSuggestion", true));
    }

    @Test
    void testPersonaResource_shouldDelegateWithAuthenticatedUser_whenCalled() {
        final var service = mock(PersonaService.class);
        final var metrics = mock(PersonaMetricsService.class);
        final var resource = new PersonaResource(service, metrics);
        final var persona = mock(PersonaResponse.class);
        final var list = List.<PersonaResponse>of();
        final var summary = mock(PersonaMetricsSummaryResponse.class);
        final var personaMetrics = mock(PersonaMetricsResponse.class);
        final var create = mock(CreatePersonaRequest.class);
        final var update = mock(UpdatePersonaRequest.class);
        when(service.list(USER_ID)).thenReturn(list);
        when(metrics.summary(1L, 2L)).thenReturn(summary);
        when(metrics.metrics("p", 1L, 2L)).thenReturn(personaMetrics);
        when(service.get(USER_ID, "p")).thenReturn(persona);
        when(service.create(USER_ID, create)).thenReturn(persona);
        when(service.update(USER_ID, "p", update)).thenReturn(persona);
        when(service.publish(USER_ID, "p", 2)).thenReturn(persona);
        when(service.unpublish(USER_ID, "p", 2)).thenReturn(persona);

        assertSame(list, resource.list(user).getResponse());
        assertSame(summary, resource.metricsSummary(1L, 2L).getResponse());
        assertSame(personaMetrics, resource.metrics("p", 1L, 2L).getResponse());
        assertSame(persona, resource.get(user, "p").getResponse());
        assertSame(persona, resource.create(user, create).getResponse());
        assertSame(persona, resource.update(user, "p", update).getResponse());
        assertSame(persona, resource.publish(user, "p", 2).getResponse());
        assertSame(persona, resource.unpublish(user, "p", 2).getResponse());
        assertTrue(resource.delete(user, "p", 2).getResponse());
        verify(service).delete(USER_ID, "p", 2);
    }

    @Test
    void testCopilotIntegrationResource_shouldDelegateWithAuthenticatedUser_whenCalled() {
        final var service = mock(CopilotConnectionService.class);
        final var executions = mock(CopilotExecutionService.class);
        final var connection = mock(CopilotConnectionResponse.class);
        final var device = mock(CopilotDeviceResponse.class);
        final var token = mock(CopilotExecutionTokenResponse.class);
        final var attempt = new CopilotAttemptRequest("a1");
        when(executions.exchange("key")).thenReturn(token);
        when(service.status(USER_ID)).thenReturn(connection);
        when(service.initiate(USER_ID)).thenReturn(device);
        when(service.poll(USER_ID, "a1")).thenReturn(device);
        when(service.cancel(USER_ID, "a1")).thenReturn(connection);
        when(service.verify(USER_ID)).thenReturn(connection);
        when(service.disconnect(USER_ID)).thenReturn(connection);
        final var resource = new CopilotIntegrationResource(service, executions);

        assertSame(token, resource.executionToken("key").getResponse());
        assertSame(connection, resource.status(user).getResponse());
        assertSame(device, resource.initiate(user).getResponse());
        assertSame(device, resource.poll(user, attempt).getResponse());
        assertSame(connection, resource.cancel(user, attempt).getResponse());
        assertSame(connection, resource.verify(user).getResponse());
        assertSame(connection, resource.disconnect(user).getResponse());
    }

    @Test
    void testCopilotNoStoreFilter_shouldSetNoStore_onlyForSensitivePaths() {
        final var filter = new CopilotNoStoreFilter();
        for (final var path : List.of("copilot-integrations/v1/status", "analysis-execution/v1/x")) {
            assertEquals("no-store", filtered(filter, path));
        }
        assertNull(filtered(filter, "merge-requests/v1/mine"));
    }

    private static String filtered(final CopilotNoStoreFilter filter, final String path) {
        final var request = mock(ContainerRequestContext.class);
        final var uriInfo = mock(UriInfo.class);
        final var response = mock(ContainerResponseContext.class);
        final var headers = new MultivaluedHashMap<String, Object>();
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn(path);
        when(response.getHeaders()).thenReturn(headers);
        filter.filter(request, response);
        return (String) headers.getFirst("Cache-Control");
    }

    @Test
    void testGroupAnalysisResource_shouldDelegate_whenCalled() {
        final var service = mock(GroupAnalysisService.class);
        final var run = mock(GroupAnalysisResponse.class);
        final var runs = List.<GroupAnalysisResponse>of();
        when(service.list("g", 5L, 10)).thenReturn(runs);
        when(service.get("r")).thenReturn(run);
        when(service.retry(USER_ID, "r")).thenReturn(run);
        final var resource = new GroupAnalysisResource(service);

        assertSame(runs, resource.list("g", 5L, 10).getResponse());
        assertSame(run, resource.get("r").getResponse());
        assertSame(run, resource.retry(user, "r").getResponse());
        assertSame(run, resource.retryPublication(user, "r").getResponse());
    }
}
