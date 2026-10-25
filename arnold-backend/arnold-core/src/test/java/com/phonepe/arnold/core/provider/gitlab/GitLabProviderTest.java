/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

import com.phonepe.arnold.core.model.vcs.VersionControlCredential;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.arnold.core.services.GitLabOAuthService;
import com.phonepe.arnold.models.mergerequest.MergeRequestResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitLabProviderTest {
    private final GitLabClient client = mock(GitLabClient.class);
    private final GitLabOAuthService oauthService = mock(GitLabOAuthService.class);
    private final GitLabProvider provider = new GitLabProvider(client, oauthService);

    @Test
    void testResolveChangeRequest_shouldUseValidUserCredential_whenUrlIsValid() {
        final var url = "https://gitlab.example.com/team/repo/-/merge_requests/12";
        final var credential = credential();
        final var mergeRequest = mergeRequest(url);
        when(oauthService.validCredential("user-1")).thenReturn(credential);
        when(client.resolveMergeRequest(url, "access-token")).thenReturn(mergeRequest);

        final var result = provider.resolveChangeRequest(url, "user-1");

        assertEquals(mergeRequest, result);
        verify(oauthService).validCredential("user-1");
        verify(client).resolveMergeRequest(url, "access-token");
    }

    @Test
    void testGetChangeRequestDiff_shouldUseValidUserCredential_whenIdentifiersAreValid() {
        final var credential = credential();
        final var diff = "diff --git a/example.txt b/example.txt";
        when(oauthService.validCredential("user-1")).thenReturn(credential);
        when(client.getMergeRequestDiff(10L, 12L, "access-token")).thenReturn(diff);

        final var result = provider.getChangeRequestDiff(10L, 12L, "user-1");

        assertEquals(diff, result);
        verify(oauthService).validCredential("user-1");
        verify(client).getMergeRequestDiff(10L, 12L, "access-token");
    }

    private static VersionControlCredential credential() {
        return new VersionControlCredential("gitlab:user-1", "user-1", VersionControlProviderType.GITLAB, "OAUTH",
                "access-token", "refresh-token", "10", "user", Set.of("api"), 1000L, "connected", 1L, 1L);
    }

    private static MergeRequestResponse mergeRequest(final String url) {
        return new MergeRequestResponse("gitlab:10:12", "GITLAB", "10", "100", 12L, "team/repo", "Title", null, url,
                "feature", "main", "abc", "opened", "can_be_merged", false, 1L, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), 0, 0, 0, false, true, 1000L, 1100L);
    }
}
