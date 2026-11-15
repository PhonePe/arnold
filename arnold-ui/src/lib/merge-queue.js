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

import { fetchMergeRequest } from './api';
import { parseChangeRequestId } from './contracts';

export async function hydrateMergeQueue(queue, { signal } = {}) {
  const ids = [...new Set(queue.items.map((item) => item.changeRequestId))];
  const detailsById = {};
  const errorsById = {};
  let cursor = 0;

  async function worker() {
    while (cursor < ids.length && !signal?.aborted) {
      const id = ids[cursor++];
      try {
        const identity = parseChangeRequestId(id);
        const detail = await fetchMergeRequest(identity.repositoryId, identity.number, { signal });
        if (!signal?.aborted) detailsById[id] = detail;
      } catch (error) {
        if (!signal?.aborted) errorsById[id] = error;
      }
    }
  }

  await Promise.all(Array.from({ length: Math.min(4, ids.length) }, worker));
  return { detailsById, errorsById };
}
