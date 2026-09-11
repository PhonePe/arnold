'use client';

import React, { Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { FileText, GitPullRequest, LockKeyhole, Search, Users, X } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { ErrorState } from '@/components/ui/ErrorState';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { LoadingState } from '@/components/ui/LoadingState';
import { Markdown } from '@/components/ui/Markdown';
import { Overline } from '@/components/ui/Overline';
import { ProgressBar } from '@/components/ui/ProgressBar';
import { UnavailableState } from '@/components/ui/UnavailableState';
import { addMergeRequestReviewer, fetchAnalysis, fetchMergeRequest, fetchPersonas, removeMergeRequestReviewer, resolveMergeRequest, searchMergeRequestReviewers, startAnalysis } from '@/lib/api';
import { parseChangeRequestId } from '@/lib/contracts';

const IMPROVE_PERSONA = 'improve';
const delayForAttempt = (attempt) => attempt <= 5 ? 2000 : attempt <= 20 ? 5000 : 10000;

function Step({ number, icon: Icon, title, description, children }) {
  return (
    <section className="border border-border bg-surface p-5 sm:p-6">
      <div className="mb-5 flex gap-4">
        <span className="grid h-8 w-8 shrink-0 place-items-center bg-fg font-mono text-xs font-bold text-surface">{number}</span>
        <div className="min-w-0">
          <h2 className="flex items-center gap-2 font-display text-lg font-bold text-fg">
            <Icon className="h-4 w-4 text-accent" aria-hidden="true" />
            {title}
          </h2>
          <p className="mt-1 text-[13px] leading-5 text-muted">{description}</p>
        </div>
      </div>
      {children}
    </section>
  );
}

function AnalyzeContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialRunId = searchParams.get('runId');
  const [url, setUrl] = useState('');
  const [mergeRequest, setMergeRequest] = useState(null);
  const [runId, setRunId] = useState(initialRunId);
  const [analysis, setAnalysis] = useState(null);
  const [status, setStatus] = useState(initialRunId ? 'polling' : 'idle');
  const [error, setError] = useState(null);
  const [pollToken, setPollToken] = useState(0);
  const [reviewerInput, setReviewerInput] = useState('');
  const [reviewerMutation, setReviewerMutation] = useState(null);
  const [reviewerError, setReviewerError] = useState(null);
  const [reviewerCandidates, setReviewerCandidates] = useState([]);
  const [reviewerSearching, setReviewerSearching] = useState(false);
  const [contextType, setContextType] = useState('rfc');
  const [contextInput, setContextInput] = useState('');
  const [contextLinks, setContextLinks] = useState([]);
  const [contextError, setContextError] = useState(null);
  const [reviewContext, setReviewContext] = useState('');
  const [personas, setPersonas] = useState(null);
  const [personaError, setPersonaError] = useState(null);
  const [selectedPersonaIds, setSelectedPersonaIds] = useState(() => new Set(
    (searchParams.get('personas') || '').split(',').filter(Boolean).slice(0, 3),
  ));

  useEffect(() => {
    if (runId) return undefined;
    const controller = new AbortController();
    fetchPersonas({ signal: controller.signal }).then((items) => {
      setPersonas(items);
      setSelectedPersonaIds((current) => new Set([...current].filter((id) => items.some((persona) => persona.id === id))));
    }).catch((nextError) => !controller.signal.aborted && setPersonaError(nextError));
    return () => controller.abort();
  }, [runId]);

  useEffect(() => {
    if (!runId || status !== 'polling') return undefined;
    const controller = new AbortController();
    let attempt = 0;
    let failures = 0;
    let timer;

    async function poll() {
      if (controller.signal.aborted) return;
      if (document.hidden) {
        timer = window.setTimeout(poll, 2000);
        return;
      }
      try {
        const nextAnalysis = await fetchAnalysis(runId, { signal: controller.signal });
        setAnalysis(nextAnalysis);
        failures = 0;
        attempt += 1;
        if (['completed', 'failed'].includes(nextAnalysis.runStatus)) {
          setStatus('completed');
        } else if (nextAnalysis.runStatus === 'running') {
          timer = window.setTimeout(poll, delayForAttempt(attempt));
        } else {
          setStatus('unknown');
        }
      } catch (nextError) {
        if (controller.signal.aborted) return;
        if (nextError.status === 404) {
          setError(nextError);
          setStatus('not-found');
          return;
        }
        failures += 1;
        if (failures >= 5) {
          setError(nextError);
          setStatus('interrupted');
        } else {
          timer = window.setTimeout(poll, delayForAttempt(attempt + 1));
        }
      }
    }
    poll();
    return () => { controller.abort(); window.clearTimeout(timer); };
  }, [runId, status, pollToken]);

  useEffect(() => {
    if (!analysis || mergeRequest) return undefined;
    const controller = new AbortController();
    const reference = parseChangeRequestId(analysis.changeRequestId);
    fetchMergeRequest(reference.repositoryId, reference.number, {
      provider: reference.provider,
      signal: controller.signal,
    }).then(setMergeRequest).catch(() => {});
    return () => controller.abort();
  }, [analysis, mergeRequest]);

  useEffect(() => {
    const query = reviewerInput.trim().replace(/^@/, '');
    if (!mergeRequest || query.length < 2 || reviewerMutation !== null) return undefined;
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setReviewerSearching(true);
      try {
        setReviewerCandidates(await searchMergeRequestReviewers(mergeRequest.repositoryId, query, {
          signal: controller.signal,
        }));
      } catch (nextError) {
        if (!controller.signal.aborted) setReviewerCandidates([]);
      } finally {
        if (!controller.signal.aborted) setReviewerSearching(false);
      }
    }, 250);
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [mergeRequest, reviewerInput, reviewerMutation]);

  async function resolve(event) {
    event.preventDefault();
    if (!url.trim()) return;
    setStatus('resolving');
    setError(null);
    try {
      setMergeRequest(await resolveMergeRequest(url.trim()));
      setReviewerInput('');
      setReviewerError(null);
      setContextLinks([]);
      setContextInput('');
      setContextError(null);
      setReviewContext('');
      setStatus('resolved');
    } catch (nextError) {
      setError(nextError);
      setStatus(nextError.status === 404 ? 'unsynchronized' : 'resolve-error');
    }
  }

  async function start() {
    setStatus('starting');
    setError(null);
    try {
      const nextAnalysis = await startAnalysis({
        mergeRequestId: mergeRequest.id,
        personaIds: [IMPROVE_PERSONA],
        rfcUrl: contextLinks.find((item) => item.type === 'rfc')?.value || null,
        jiraTickets: contextLinks.filter((item) => item.type === 'jira').map((item) => item.value),
        reviewContext: reviewContext.trim() || null,
        reviewPersonaIds: [...selectedPersonaIds],
      });
      setAnalysis(nextAnalysis);
      setRunId(nextAnalysis.runId);
      setStatus(['completed', 'failed'].includes(nextAnalysis.runStatus) ? 'completed' : 'polling');
      router.replace(`/history?runId=${encodeURIComponent(nextAnalysis.runId)}`);
    } catch (nextError) {
      setError(nextError);
      setStatus('start-error');
    }
  }

  async function saveReviewer(username) {
    if (!mergeRequest || !username) return;
    setReviewerMutation('adding');
    setReviewerError(null);
    setReviewerCandidates([]);
    try {
      setMergeRequest(await addMergeRequestReviewer(mergeRequest.repositoryId, mergeRequest.changeNumber, username));
      setReviewerInput('');
    } catch (nextError) {
      setReviewerError(nextError);
    } finally {
      setReviewerMutation(null);
    }
  }

  function addReviewer(event) {
    event.preventDefault();
    saveReviewer(reviewerInput.trim().replace(/^@/, ''));
  }

  function addContext(event) {
    event.preventDefault();
    const value = contextInput.trim();
    const normalized = contextType === 'jira'
      ? value.toUpperCase().match(/[A-Z]{2,10}-\d+/)?.[0]
      : value;
    if (!normalized) {
      setContextError('Enter a Jira ticket key or URL containing a ticket key.');
      return;
    }
    setContextLinks((items) => contextType === 'rfc'
      ? [...items.filter((item) => item.type !== 'rfc'), { type: 'rfc', value: normalized }]
      : items.some((item) => item.type === 'jira' && item.value === normalized)
        ? items
        : [...items, { type: 'jira', value: normalized }]);
    setContextInput('');
    setContextError(null);
  }

  async function removeReviewer(reviewerId) {
    setReviewerMutation(reviewerId);
    setReviewerError(null);
    try {
      setMergeRequest(await removeMergeRequestReviewer(mergeRequest.repositoryId, mergeRequest.changeNumber, reviewerId));
    } catch (nextError) {
      setReviewerError(nextError);
    } finally {
      setReviewerMutation(null);
    }
  }

  function togglePersona(personaId) {
    setSelectedPersonaIds((current) => {
      const next = new Set(current);
      if (next.has(personaId)) next.delete(personaId);
      else if (next.size < 3) next.add(personaId);
      return next;
    });
  }

  if (runId) {
    const failed = analysis?.runStatus === 'failed' || analysis?.analysisOutcome === 'FAILED';
    const finished = analysis?.runStatus === 'completed' || failed;
    return (
      <div className="mx-auto max-w-[1000px]">
        <div className="mb-6">
          <Overline>Clio Improve</Overline>
          <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">Analysis run</h1>
          <p className="mt-2 text-[15px] text-muted">Inline code suggestions are generated and posted on GitLab.</p>
        </div>

        {status === 'polling' && !analysis ? <LoadingState label="Loading analysis run" /> : null}
        {status === 'not-found' ? <ErrorState title="Analysis run not found" error={error} /> : null}
        {status === 'interrupted' ? (
          <ErrorState title="Analysis polling interrupted" error={error} onRetry={() => { setError(null); setStatus('polling'); setPollToken((value) => value + 1); }} />
        ) : null}
        {status === 'unknown' ? <UnavailableState title="Unknown analysis status" description={`Arnold returned ${analysis?.runStatus || 'an unknown status'}.`} /> : null}

        {analysis ? (
          <section className={`border border-border border-l-[3px] bg-surface p-5 sm:p-6 ${failed ? 'border-l-bad' : finished ? 'border-l-ok' : 'border-l-accent'}`}>
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <Overline tone="faint">{mergeRequest?.repositoryPath || analysis.changeRequestId}</Overline>
                <h2 className="mt-1 font-display text-2xl font-bold text-fg">{mergeRequest?.title || 'Improve analysis'}</h2>
                {mergeRequest ? <p className="mt-2 font-mono text-xs text-muted">!{mergeRequest.changeNumber} · {mergeRequest.sourceBranch} → {mergeRequest.targetBranch}</p> : null}
              </div>
              <Badge variant={failed ? 'error' : finished ? 'success' : 'warning'}>{failed ? 'Failed' : finished ? 'Completed' : 'Running'}</Badge>
            </div>

            <div className="mt-7 grid gap-px border border-border bg-border sm:grid-cols-3">
              <div className="bg-surface-2 p-4"><span className="font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Arnold run</span><p className="mt-1 break-all font-mono text-xs text-fg">{analysis.runId}</p></div>
              <div className="bg-surface-2 p-4"><span className="font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Clio pipeline</span><p className="mt-1 font-mono text-xs text-fg">{analysis.pipelineId || 'Starting…'}</p></div>
              <div className="bg-surface-2 p-4"><span className="font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Review state</span><p className="mt-1 font-mono text-xs text-fg">{analysis.reviewState.replaceAll('_', ' ')}</p></div>
            </div>

            <div className="mt-6">
              <div className="mb-2 flex justify-between font-mono text-[11px] text-muted"><span>{finished ? 'Workflow finished' : 'Clio is reviewing the merge request'}</span><span>{finished ? '100%' : 'In progress'}</span></div>
              <ProgressBar value={finished ? 1 : 0} max={1} color={failed ? 'error' : finished ? 'success' : 'primary'} />
            </div>
            {analysis.outcomeDetail ? <Markdown className={`mt-5 border border-border bg-surface-2 p-3 ${failed ? 'text-bad' : 'text-muted'}`}>{analysis.outcomeDetail}</Markdown> : null}
            <p className="mt-5 font-mono text-[10px] text-faint">Created {new Date(analysis.createdAt).toLocaleString()} · Updated {new Date(analysis.updatedAt).toLocaleString()}</p>
          </section>
        ) : null}
      </div>
    );
  }

  const reviewers = (mergeRequest?.reviewerIds || []).map((id, index) => ({
    id,
    name: mergeRequest.reviewerNames[index] || `GitLab user ${id}`,
  }));
  const availableReviewerCandidates = reviewerCandidates.filter((candidate) =>
    !mergeRequest?.reviewerIds.includes(candidate.id));
  return (
    <div className="mx-auto max-w-[1280px]">
      <div className="mb-6">
        <Overline>New analysis</Overline>
        <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">Run analysis on your code</h1>
        <p className="mt-2 max-w-[76ch] text-[15px] text-muted">Select a synchronized merge request and Arnold will run Clio Improve to post inline suggestions on GitLab.</p>
      </div>

      <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="grid gap-4">
          <Step number="1" icon={GitPullRequest} title="What are you analysing?" description="V1 supports one synchronized GitLab merge request.">
            <div className="mb-5 grid grid-cols-2 border border-border-2 bg-surface-2 p-1 font-mono text-xs">
              <span className="bg-fg px-3 py-2 text-center font-semibold text-surface">Single MR</span>
              <button type="button" className="px-3 py-2 text-faint" disabled title="MR groups are not available yet">MR group · later</button>
            </div>
            <form onSubmit={resolve} className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
              <Input className="font-mono" value={url} onChange={(event) => setUrl(event.target.value)} placeholder="https://gitlab.example/team/repo/-/merge_requests/123" aria-label="GitLab merge request URL" disabled={status === 'resolving'} />
              <Button type="submit" variant="secondary" disabled={!url.trim() || status === 'resolving'}><Search className="h-4 w-4" aria-hidden="true" />{status === 'resolving' ? 'Fetching…' : 'Fetch details'}</Button>
            </form>
            {status === 'unsynchronized' ? <ErrorState className="mt-4" title="Merge request not synchronized" error={{ message: 'Sync this merge request into Arnold before running analysis.' }} /> : null}
            {status === 'resolve-error' ? <ErrorState className="mt-4" title="Could not resolve merge request" error={error} /> : null}
            {mergeRequest ? (
              <div className="mt-4 border border-ok bg-surface-2 p-4">
                <div className="flex flex-wrap items-start justify-between gap-3"><div><h3 className="font-display text-lg font-bold text-fg">{mergeRequest.title}</h3><p className="mt-1 font-mono text-[11px] text-muted">{mergeRequest.repositoryPath} · !{mergeRequest.changeNumber} · {mergeRequest.sourceBranch} → {mergeRequest.targetBranch}</p></div><Badge variant="success">Synchronized</Badge></div>
                <Label className="mt-4 block">Merge request description</Label>
                <div className="mt-2 max-h-64 overflow-y-auto border border-border bg-surface p-3">
                  {mergeRequest.description ? <Markdown>{mergeRequest.description}</Markdown> : <p className="text-sm text-faint">No description provided.</p>}
                </div>
              </div>
            ) : null}
          </Step>

          <Step number="2" icon={FileText} title="Context for the review" description="Attach an RFC or Jira ticket so Improve can validate the implementation against its requirements.">
            <form onSubmit={addContext} className="grid gap-2 sm:grid-cols-[150px_minmax(0,1fr)_auto]">
              <select className="h-9 border border-border-2 bg-surface-2 px-3 font-mono text-xs text-fg focus:border-accent focus:outline-none focus:ring-[3px] focus:ring-[var(--accent-soft)]" value={contextType} onChange={(event) => { setContextType(event.target.value); setContextError(null); }} aria-label="Review context type" disabled={!mergeRequest}>
                <option value="rfc">RFC</option>
                <option value="jira">Jira ticket</option>
              </select>
              <Input type={contextType === 'rfc' ? 'url' : 'text'} value={contextInput} onChange={(event) => setContextInput(event.target.value)} placeholder={contextType === 'rfc' ? 'https://docs.example/rfc' : 'ACME-0 or Jira URL'} aria-label={contextType === 'rfc' ? 'RFC URL' : 'Jira ticket'} disabled={!mergeRequest} required />
              <Button type="submit" variant="secondary" disabled={!mergeRequest || !contextInput.trim()}>Attach</Button>
            </form>
            {contextLinks.length ? <div className="mt-3 grid gap-2">{contextLinks.map((item) => (
              <div key={`${item.type}:${item.value}`} className="flex min-w-0 items-center gap-3 border border-border bg-surface-2 px-3 py-2">
                <Badge variant={item.type === 'rfc' ? 'info' : 'default'}>{item.type}</Badge>
                <span className="min-w-0 flex-1 truncate font-mono text-xs text-fg">{item.value}</span>
                <button type="button" className="grid h-7 w-7 place-items-center text-faint hover:text-bad focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" onClick={() => setContextLinks((items) => items.filter((candidate) => candidate !== item))} aria-label={`Remove ${item.value}`}><X className="h-4 w-4" aria-hidden="true" /></button>
              </div>
            ))}</div> : <p className="mt-3 text-xs text-muted">Optional. RFC and Jira references in the MR title, description, or branch may be detected automatically.</p>}
            {contextError ? <p className="mt-2 text-xs text-bad" role="alert">{contextError}</p> : null}
            <Label htmlFor="review-context" className="mt-5 block">Extra context for the review</Label>
            <textarea id="review-context" className="mt-2 min-h-24 w-full resize-y border border-border-2 bg-surface-2 p-3 text-sm text-fg focus:border-accent focus:outline-none focus:ring-[3px] focus:ring-[var(--accent-soft)] disabled:opacity-50" value={reviewContext} onChange={(event) => setReviewContext(event.target.value)} placeholder="Describe requirements, risk areas, or anything Improve should pay special attention to." disabled={!mergeRequest} maxLength={4000} />
          </Step>

          <Step number="3" icon={LockKeyhole} title="Review personas" description="Optionally combine up to three focused reviewers with the standard Improve review.">
            {!personas && !personaError ? <LoadingState label="Loading review personas" /> : null}
            {personaError ? <ErrorState title="Could not load review personas" error={personaError} /> : null}
            {personas ? <div className="grid gap-2 sm:grid-cols-2">{personas.map((persona) => {
              const selected = selectedPersonaIds.has(persona.id);
              const disabled = !selected && selectedPersonaIds.size >= 3;
              return <button key={persona.id} type="button" disabled={disabled} onClick={() => togglePersona(persona.id)} className={`flex items-start gap-3 border p-3 text-left transition-colors disabled:opacity-40 ${selected ? 'border-accent bg-[var(--accent-soft)]' : 'border-border bg-surface-2 hover:border-accent'}`} aria-pressed={selected}><span className="grid h-9 w-9 shrink-0 place-items-center bg-fg font-display font-bold text-surface">{persona.name[0]}</span><span className="min-w-0 flex-1"><span className="flex items-center gap-2 text-sm font-semibold text-fg">{persona.name}{persona.status === 'DRAFT' ? <Badge variant="warning">Draft</Badge> : null}</span><span className="mt-1 block text-xs leading-5 text-muted">{persona.summary}</span></span>{selected ? <Badge variant="success">Selected</Badge> : null}</button>;
            })}</div> : null}
            <p className="mt-3 font-mono text-[10px] text-muted">{selectedPersonaIds.size}/3 selected · no selection runs standard Improve</p>
          </Step>

          <Step number="4" icon={Users} title="Reviewers" description="Add or remove GitLab reviewers before starting the analysis.">
            {mergeRequest ? (reviewers.length ? <div className="flex flex-wrap gap-2">{reviewers.map((reviewer) => (
              <span key={reviewer.id} className="inline-flex items-center gap-2 border border-border-2 bg-surface-2 py-1.5 pl-3 pr-1.5 text-sm font-semibold text-fg">
                {reviewer.name}<Badge>GitLab</Badge>
                <button type="button" className="grid h-7 w-7 place-items-center text-faint transition-colors hover:text-bad focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50" onClick={() => removeReviewer(reviewer.id)} disabled={reviewerMutation !== null} aria-label={`Remove ${reviewer.name}`}><X className="h-4 w-4" aria-hidden="true" /></button>
              </span>
            ))}</div> : <p className="text-sm text-muted">No reviewers are assigned on GitLab.</p>) : <p className="text-sm text-faint">Fetch a merge request to manage its reviewers.</p>}
            <form className="mt-4 flex gap-2" onSubmit={addReviewer}>
              <div className="relative min-w-0 flex-1">
                <Input value={reviewerInput} onChange={(event) => { setReviewerInput(event.target.value); if (event.target.value.trim().length < 2) setReviewerCandidates([]); }} onBlur={() => window.setTimeout(() => setReviewerCandidates([]), 100)} placeholder="Search reviewer by name or handle" aria-label="Search GitLab reviewers" autoComplete="off" disabled={!mergeRequest || reviewerMutation !== null} />
                {reviewerSearching ? <span className="absolute right-3 top-1/2 -translate-y-1/2 font-mono text-[10px] text-faint">Searching…</span> : null}
                {availableReviewerCandidates.length ? <div className="absolute z-20 mt-1 max-h-64 w-full overflow-y-auto border border-border-2 bg-surface-2 shadow-[0_14px_38px_rgba(28,26,22,0.16)]" role="listbox" aria-label="GitLab reviewer suggestions">
                  {availableReviewerCandidates.map((candidate) => (
                    <button key={candidate.id} type="button" className="flex w-full items-center gap-3 border-b border-border px-3 py-2.5 text-left last:border-b-0 hover:bg-[var(--accent-soft)] focus-visible:outline-none focus-visible:bg-[var(--accent-soft)]" onMouseDown={(event) => event.preventDefault()} onClick={() => saveReviewer(candidate.username)} role="option" aria-selected="false">
                      <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-fg font-mono text-[10px] font-bold text-surface">{candidate.name.slice(0, 2).toUpperCase()}</span>
                      <span className="min-w-0"><span className="block truncate text-sm font-semibold text-fg">{candidate.name}</span><span className="block truncate font-mono text-[10px] text-muted">@{candidate.username}</span></span>
                    </button>
                  ))}
                </div> : null}
              </div>
              <Button type="submit" variant="secondary" disabled={!mergeRequest || !reviewerInput.trim() || reviewerMutation !== null}>{reviewerMutation === 'adding' ? 'Adding…' : 'Add'}</Button>
            </form>
            {mergeRequest ? <p className="mt-3 font-mono text-[10px] text-muted">Reviewer changes are saved to GitLab immediately.</p> : null}
            {reviewerError ? <ErrorState className="mt-4" title="Could not update reviewers" error={reviewerError} /> : null}
          </Step>

          <Step number="5" icon={LockKeyhole} title="Visibility" description="Improve starts immediately and posts progress and suggestions directly on the GitLab merge request.">
            <div className="flex items-start gap-3 border border-border bg-surface-2 p-4 opacity-70"><input className="mt-1 h-4 w-4 accent-[var(--accent)]" type="checkbox" checked disabled readOnly /><div><p className="text-sm font-semibold text-fg">Start analysis immediately</p><p className="mt-1 text-xs leading-5 text-muted">Draft visibility controls are not available for Clio Improve.</p></div></div>
          </Step>
        </div>

        <aside className="grid gap-3 lg:sticky lg:top-20">
          <section className="border border-border bg-surface p-5">
            <Overline tone="faint">Analysis summary</Overline>
            <dl className="mt-4 divide-y divide-border border-y border-border">
              <div className="flex justify-between gap-4 py-3"><dt className="font-mono text-xs text-muted">Scope</dt><dd className="text-sm font-semibold text-fg">Single MR</dd></div>
              <div className="flex justify-between gap-4 py-3"><dt className="font-mono text-xs text-muted">Workflow</dt><dd className="text-sm font-semibold text-fg">Clio Improve</dd></div>
              <div className="flex justify-between gap-4 py-3"><dt className="font-mono text-xs text-muted">Context</dt><dd className="text-sm font-semibold text-fg">{contextLinks.length + (reviewContext.trim() ? 1 : 0) || 'Auto-detect'}</dd></div>
              <div className="flex justify-between gap-4 py-3"><dt className="font-mono text-xs text-muted">Personas</dt><dd className="text-sm font-semibold text-fg">{selectedPersonaIds.size || 'Standard'}</dd></div>
              <div className="flex justify-between gap-4 py-3"><dt className="font-mono text-xs text-muted">Reviewers</dt><dd className="text-sm font-semibold text-fg">{mergeRequest ? reviewers.length : '—'}</dd></div>
              <div className="flex justify-between gap-4 py-3"><dt className="font-mono text-xs text-muted">Status</dt><dd><Badge variant={mergeRequest ? 'success' : 'warning'}>{mergeRequest ? 'Ready' : 'Select MR'}</Badge></dd></div>
            </dl>
          </section>
          <section className="border border-border bg-surface p-5">
            <Button className="w-full" size="lg" onClick={start} disabled={!mergeRequest || status === 'starting'}>{status === 'starting' ? 'Starting Improve…' : 'Analyse'}</Button>
            <Button className="mt-2 w-full" variant="ghost" onClick={() => router.push('/queue')}>Cancel</Button>
            <p className="mt-3 text-center font-mono text-[10px] leading-4 text-muted">Suggestions are posted directly to the selected GitLab MR.</p>
            {status === 'start-error' ? <ErrorState className="mt-4" title="Could not start analysis" error={error} /> : null}
          </section>
        </aside>
      </div>
    </div>
  );
}

export default function AnalyzePage() {
  return <Suspense fallback={<LoadingState label="Loading analysis" />}><AnalyzeContent /></Suspense>;
}
