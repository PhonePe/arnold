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

export function groupDiscussions(discussions) {
  const groups = new Map();
  discussions.forEach((discussion) => {
    const key = discussion.individualNote ? `note:${discussion.id}` : discussion.threadId || `note:${discussion.id}`;
    const thread = groups.get(key) || { id: key, discussionId: discussion.threadId, individualNote: discussion.individualNote, notes: [], position: null };
    thread.notes.push(discussion);
    thread.position ||= discussion.position;
    groups.set(key, thread);
  });
  return [...groups.values()].map((thread) => ({
    ...thread,
    notes: thread.notes.sort((left, right) => left.createdAt - right.createdAt),
    resolvable: thread.notes.some((note) => note.resolvable),
    resolved: thread.notes.some((note) => note.resolved),
  })).sort((left, right) => left.notes[0].createdAt - right.notes[0].createdAt);
}
