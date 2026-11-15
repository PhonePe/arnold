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

import com.phonepe.arnold.core.exception.ArnoldError;
import com.phonepe.arnold.core.exception.ArnoldErrorCode;
import com.phonepe.arnold.core.model.vcs.VersionControlProviderType;
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
