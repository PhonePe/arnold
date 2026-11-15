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

package com.phonepe.arnold.core.provider.gitlab;

import com.phonepe.arnold.core.model.vcs.PublishedComment;
import com.phonepe.arnold.core.model.vcs.VersionControlCredential;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.GitLabOAuthService;
import com.phonepe.arnold.models.comment.CreateDiscussionRequest;
import com.phonepe.arnold.models.comment.PublishReviewRequest;
import com.phonepe.arnold.models.comment.ResolveDiscussionRequest;
import com.phonepe.arnold.models.comment.UpdateDraftRequest;
import com.phonepe.arnold.models.mergerequest.MergeRequestDiffVersionResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestFilesResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestResponse;
import com.phonepe.arnold.models.mergerequest.MergeRequestReviewResponse;
import com.phonepe.arnold.models.mergerequest.ReviewerCandidateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitLabProviderDelegationTest {
    private static final String TOKEN = "access-token";
    private final GitLabClient client = mock(GitLabClient.class);
    private final GitLabOAuthService oauthService = mock(GitLabOAuthService.class);
    private final GitLabProvider provider = new GitLabProvider(client, oauthService);

    @BeforeEach
    void setUp() {
        when(oauthService.validCredential("u1")).thenReturn(new VersionControlCredential("gitlab:u1", "u1",
                VersionControlProviderType.GITLAB, "OAUTH", TOKEN, "refresh", "10", "gl-user", Set.of("api"),
                1000L, "connected", 1L, 1L));
    }

    @Test
    void testType_shouldBeGitlab() {
        assertEquals(VersionControlProviderType.GITLAB, provider.type());
    }

    @Test
    void testReviewerOperations_shouldUseUserToken() {
        final var mr = mock(MergeRequestResponse.class);
        final var candidates = List.of(mock(ReviewerCandidateResponse.class));
        when(client.addReviewer(1L, 2L, "bob", TOKEN)).thenReturn(mr);
        when(client.removeReviewer(1L, 2L, 3L, TOKEN)).thenReturn(mr);
        when(client.searchReviewers(1L, "bo", TOKEN)).thenReturn(candidates);

        assertSame(mr, provider.addChangeRequestReviewer(1L, 2L, "bob", "u1"));
        assertSame(mr, provider.removeChangeRequestReviewer(1L, 2L, 3L, "u1"));
        assertSame(candidates, provider.searchChangeRequestReviewers(1L, "bo", "u1"));
    }

    @Test
    void testReviewAndDiffReads_shouldUseUserToken() {
        final var review = mock(MergeRequestReviewResponse.class);
        final var versions = List.of(mock(MergeRequestDiffVersionResponse.class));
        final var files = mock(MergeRequestFilesResponse.class);
        when(client.getMergeRequestReview(1L, 2L, TOKEN)).thenReturn(review);
        when(client.getMergeRequestDiffVersion(1L, 2L, 7L, TOKEN)).thenReturn("diff");
        when(client.listMergeRequestDiffVersions(1L, 2L, TOKEN)).thenReturn(versions);
        when(client.getMergeRequestFiles(1L, 2L, "o", "n", "b", "h", TOKEN)).thenReturn(files);

        assertSame(review, provider.getChangeRequestReview(1L, 2L, "u1"));
        assertEquals("diff", provider.getChangeRequestDiffVersion(1L, 2L, 7L, "u1"));
        assertSame(versions, provider.listChangeRequestDiffVersions(1L, 2L, "u1"));
        assertSame(files, provider.getChangeRequestFiles(1L, 2L, "o", "n", "b", "h", "u1"));
    }

    @Test
    void testDiscussionAndDraftOperations_shouldUseCredential() {
        final var create = mock(CreateDiscussionRequest.class);
        final var update = mock(UpdateDraftRequest.class);
        final var comment = mock(PublishedComment.class);
        final var comments = List.of(comment);
        when(client.createDiscussion(create, TOKEN)).thenReturn(comment);
        when(client.listDiscussions(1L, 2L, TOKEN)).thenReturn(comments);
        when(client.listDrafts(1L, 2L, TOKEN, "gl-user")).thenReturn(comments);
        when(client.createDraft(create, TOKEN, "gl-user")).thenReturn(comment);
        when(client.updateDraft(5L, update, TOKEN, "gl-user")).thenReturn(comment);

        assertSame(comment, provider.createDiscussion(create, "u1"));
        assertSame(comments, provider.listDiscussions(1L, 2L, "u1"));
        assertSame(comments, provider.listDrafts(1L, 2L, "u1"));
        assertSame(comment, provider.createDraft(create, "u1"));
        assertSame(comment, provider.updateDraft(5L, update, "u1"));
    }

    @Test
    void testWriteOperations_shouldUseUserToken() {
        final var publish = mock(PublishReviewRequest.class);
        final var resolve = mock(ResolveDiscussionRequest.class);

        provider.deleteDraft(5L, 1L, 2L, "u1");
        provider.publishDrafts(publish, "u1");
        provider.resolveDiscussion("d1", resolve, "u1");
        provider.applySuggestion(9L, "msg", "u1");

        verify(client).deleteDraft(5L, 1L, 2L, TOKEN);
        verify(client).publishDrafts(publish, TOKEN);
        verify(client).resolveDiscussion("d1", resolve, TOKEN);
        verify(client).applySuggestion(9L, "msg", TOKEN);
    }
}
