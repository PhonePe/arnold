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

package com.phonepe.arnold.models.es;

public record CommentDocument(
        String id,
        String mergeRequestId,
        String discussionId,
        long noteId,
        Long authorId,
        String body,
        boolean resolved,
        boolean deleted,
        String source,
        long updatedAt,
        long createdAt,
        String authorUsername,
        String state,
        boolean resolvable,
        boolean individualNote,
        Long mergeRequestAuthorId,
        boolean byMergeRequestAuthor,
        String analysisRunId,
        String personaId,
        Long personaVersion
) {
}
