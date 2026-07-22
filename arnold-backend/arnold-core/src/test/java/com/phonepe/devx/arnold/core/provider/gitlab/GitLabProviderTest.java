package com.phonepe.devx.arnold.core.provider.gitlab;

import com.phonepe.devx.arnold.core.model.vcs.VersionControlCredential;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.services.GitLabOAuthService;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;
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

    private static VersionControlCredential credential() {
        return new VersionControlCredential("gitlab:user-1", "user-1", VersionControlProviderType.GITLAB, "OAUTH",
                "access-token", "refresh-token", "10", "user", Set.of("api"), 1000L, "connected", 1L, 1L);
    }

    private static MergeRequestResponse mergeRequest(final String url) {
        return new MergeRequestResponse("gitlab:10:12", 10L, 100L, 12L, "team/repo", "Title", null, url,
                "feature", "main", "abc", "opened", "can_be_merged", false, 1L, List.of(), List.of(),
                List.of(), 1000L, 1100L);
    }
}
