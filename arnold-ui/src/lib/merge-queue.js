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
