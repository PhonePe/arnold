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

export function reviewHref({ provider = 'GITLAB', projectId, iid, runId } = {}) {
  const base = `/review/${encodeURIComponent(provider).toUpperCase()}/${encodeURIComponent(projectId)}/${encodeURIComponent(iid)}`;
  return runId ? `${base}/runs/${encodeURIComponent(runId)}` : base;
}

export function reviewHrefFromChangeRequestId(changeRequestId, runId) {
  const [provider, repositoryId, number] = String(changeRequestId).split(':');
  return reviewHref({ provider, projectId: repositoryId, iid: number, runId });
}

export function analysisHref(changeRequestId, personaIds = [], execution = null) {
  const params = new URLSearchParams({ mergeRequestId: changeRequestId });
  const personas = personaIds.filter(Boolean).slice(0, 3);
  if (personas.length) params.set('personas', personas.join(','));
  if (execution?.harness) params.set('harness', execution.harness);
  if (execution?.model) params.set('model', execution.model);
  return `/analyze?${params}`;
}

export function analysisEntry(reviewState, changeRequestId) {
  const state = String(reviewState || '').toUpperCase();
  if (['CLOSED', 'MERGED'].includes(state)) return null;
  if (state === 'ANALYSING') {
    return {
      href: `/history?${new URLSearchParams({ changeRequestId })}`,
      label: 'View analysis',
    };
  }
  return {
    href: analysisHref(changeRequestId),
    label: ['ANALYSED', 'REVIEW_READY'].includes(state) ? 'Run again' : 'Run analysis',
  };
}

export function runPersonaIds(run) {
  const ids = run.reviewPersonas?.length ? run.reviewPersonas.map((persona) => persona.id) : run.personaIds || [];
  return ids.filter((id) => id !== 'improve').slice(0, 3);
}

export function pastRunsFor(scope, target, singleRuns = [], groupRuns = []) {
  if (!target) return [];
  return (scope === 'group'
    ? groupRuns.filter((run) => run.groupId === target.id)
    : singleRuns.filter((run) => run.changeRequestId === target.id))
    .sort((a, b) => b.createdAt - a.createdAt);
}
