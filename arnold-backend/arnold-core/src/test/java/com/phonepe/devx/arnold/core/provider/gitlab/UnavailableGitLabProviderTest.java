package com.phonepe.devx.arnold.core.provider.gitlab;

import com.phonepe.devx.arnold.core.exception.ArnoldError;
import com.phonepe.devx.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.devx.arnold.core.model.vcs.VersionControlProviderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnavailableGitLabProviderTest {
    private final UnavailableGitLabProvider provider = new UnavailableGitLabProvider();

    @Test
    void testType_shouldBeGitlab() {
        assertEquals(VersionControlProviderType.GITLAB, provider.type());
    }

    @Test
    void testEveryOperation_shouldFailWithClientFailure() {
        final List<Executable> operations = List.of(
                () -> provider.resolveChangeRequest("url", "u"),
                () -> provider.addChangeRequestReviewer(1L, 2L, "n", "u"),
                () -> provider.removeChangeRequestReviewer(1L, 2L, 3L, "u"),
                () -> provider.searchChangeRequestReviewers(1L, "q", "u"),
                () -> provider.triggerSmartDiff("url", 1L, 2L, "sha", "e"),
                () -> provider.getChangeRequestReview(1L, 2L, "u"),
                () -> provider.getChangeRequestDiff(1L, 2L, "u"),
                () -> provider.getChangeRequestDiffVersion(1L, 2L, 3L, "u"),
                () -> provider.listChangeRequestDiffVersions(1L, 2L, "u"),
                () -> provider.getChangeRequestFiles(1L, 2L, "o", "n", "b", "h", "u"),
                () -> provider.createDiscussion(null, "u"),
                () -> provider.listDiscussions(1L, 2L, "u"),
                () -> provider.listDrafts(1L, 2L, "u"),
                () -> provider.createDraft(null, "u"),
                () -> provider.updateDraft(1L, null, "u"),
                () -> provider.deleteDraft(1L, 2L, 3L, "u"),
                () -> provider.publishDrafts(null, "u"),
                () -> provider.resolveDiscussion("d", null, "u"),
                () -> provider.applySuggestion(1L, "m", "u"));

        for (final var operation : operations) {
            final var error = assertThrows(ArnoldError.class, operation);
            assertEquals(ArnoldErrorCode.CLIENT_FAILURE, error.getErrorCode());
        }
    }
}
