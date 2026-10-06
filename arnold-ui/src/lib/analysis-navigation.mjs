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
