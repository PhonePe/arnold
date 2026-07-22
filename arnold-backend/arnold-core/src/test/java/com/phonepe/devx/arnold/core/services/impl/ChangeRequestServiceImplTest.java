package com.phonepe.devx.arnold.core.services.impl;

import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import com.phonepe.devx.arnold.core.provider.VersionControlProvider;
import com.phonepe.devx.arnold.core.provider.VersionControlProviders;
import com.phonepe.devx.arnold.core.storage.aerospike.AerospikeChangeRequestStore;
import com.phonepe.devx.arnold.models.mergerequest.MergeRequestResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChangeRequestServiceImplTest {
    private final AerospikeChangeRequestStore changeRequestStore = mock(AerospikeChangeRequestStore.class);
    private final VersionControlProviders providers = mock(VersionControlProviders.class);
    private final VersionControlProvider gitLabProvider = mock(VersionControlProvider.class);
    private final ChangeRequestServiceImpl service = new ChangeRequestServiceImpl(changeRequestStore, providers);

    @Test
    void testResolve_shouldResolveFromGitLabProvider_whenUrlIsValid() {
        final var url = "https://gitlab.example/team/repo/-/merge_requests/12";
        final var mergeRequest = mergeRequest();
        when(providers.get(VersionControlProviderType.GITLAB)).thenReturn(gitLabProvider);
        when(gitLabProvider.resolveChangeRequest(url, "user-1")).thenReturn(mergeRequest);

        final var result = service.resolve(url, "user-1");

        assertEquals(mergeRequest, result);
        verify(providers).get(VersionControlProviderType.GITLAB);
        verify(gitLabProvider).resolveChangeRequest(url, "user-1");
        verifyNoInteractions(changeRequestStore);
    }

    private static MergeRequestResponse mergeRequest() {
        return new MergeRequestResponse("gitlab:10:12", 10L, 100L, 12L, "team/repo", "Title", null,
                "https://gitlab.example/team/repo/-/merge_requests/12", "feature", "main", "abc", "opened",
                "can_be_merged", false, 1L, List.of(2L), List.of(3L), List.of("backend"), 1000L, 1100L);
    }
}
