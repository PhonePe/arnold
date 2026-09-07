package com.phonepe.devx.arnold.core.model.vcs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeRequestRecordTest {

    @Test
    void testReadyForMerge_shouldRequireOpenMergeableNonDraftChange() {
        assertTrue(changeRequest("opened", "mergeable", false).readyForMerge());
        assertTrue(changeRequest("open", "can_be_merged", false).readyForMerge());
        assertFalse(changeRequest("closed", "mergeable", false).readyForMerge());
        assertFalse(changeRequest("opened", "checking", false).readyForMerge());
        assertFalse(changeRequest("opened", "mergeable", true).readyForMerge());
    }

    private ChangeRequestRecord changeRequest(final String state, final String mergeStatus, final boolean draft) {
        return new ChangeRequestRecord("gitlab:1:2", VersionControlProviderType.GITLAB, "1", "2", 2,
                "group/project", "Title", "Description", "https://gitlab.example/group/project/-/merge_requests/2",
                "feature", "main", "sha", state, mergeStatus, draft, 1L, List.of(), List.of(), List.of(), 2, 3);
    }
}
