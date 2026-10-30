'use client';

import React, { Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Play, RefreshCw, Search, SlidersHorizontal } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { ErrorState } from '@/components/ui/ErrorState';
import { Input } from '@/components/ui/Input';
import { LoadingState } from '@/components/ui/LoadingState';
import { Overline } from '@/components/ui/Overline';
import { UnavailableState } from '@/components/ui/UnavailableState';
import { fetchAnalyses, fetchGroupAnalyses, fetchUsers } from '@/lib/api';
import { AnalysisFindings } from '@/components/composite/AnalysisFindings';
import { AnalysisRunDetail } from '@/components/composite/AnalysisRunDetail';
import { GroupAnalysisDetail } from '@/components/composite/GroupAnalysisDetail';
import { parseChangeRequestId } from '@/lib/contracts';
import { analysisHref, reviewHrefFromChangeRequestId } from '@/lib/analysis-navigation.mjs';

const terminalStatuses = new Set(['completed', 'failed', 'partial', 'stale']);

function presentation(run) {
  if (run.runStatus === 'partial') return { label: 'Partial', variant: 'warning', border: 'border-l-tertiary', progress: 'tertiary' };
  if (run.runStatus === 'stale') return { label: 'Stale', variant: 'warning', border: 'border-l-tertiary', progress: 'tertiary' };
  if (run.runStatus === 'failed' || run.analysisOutcome === 'FAILED') {
    return { label: 'Failed', variant: 'error', border: 'border-l-bad', progress: 'error' };
  }
  if (run.runStatus === 'completed') {
    return { label: 'Completed', variant: 'success', border: 'border-l-ok', progress: 'success' };
  }
  return { label: 'Running', variant: 'warning', border: 'border-l-tertiary', progress: 'tertiary' };
}

function mergeRequestLabel(run) {
  if (run.repositoryPath) return `${run.repositoryPath} !${parseChangeRequestId(run.changeRequestId).number}`;
  const { repositoryId, number } = parseChangeRequestId(run.changeRequestId);
  return `Project ${repositoryId} · MR !${number}`;
}

function runTitle(run) {
  if (run.groupId) return `${run.groupName} · ${run.members.length} MRs`;
  return run.title || mergeRequestLabel(run);
}

function runSearchText(run) {
  return `${run.runId} ${run.groupId ? run.groupName : `${run.title || ''} ${mergeRequestLabel(run)} ${run.changeRequestId}`} ${run.invokedBy || ''} ${run.pipelineId || ''}`.toLowerCase();
}

const PAGE_SIZE = 50;
// ponytail: hard cap per endpoint for the merged list; raise or add server-side search when hit.
const MAX_PAGES = 10;

async function fetchRunPage({ before } = {}) {
  const [singleRuns, groupRuns] = await Promise.all([
    fetchAnalyses({ before, limit: PAGE_SIZE }),
    fetchGroupAnalyses({ before, limit: PAGE_SIZE }),
  ]);
  return [...singleRuns, ...groupRuns].sort((a, b) => b.createdAt - a.createdAt);
}

function HistoryContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const selectedRunId = searchParams.get('runId');
  const selectedChangeRequestId = searchParams.get('changeRequestId');
  const [runs, setRuns] = useState(null);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(null);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('all');
  const [invokerId, setInvokerId] = useState('all');
  const [users, setUsers] = useState(null);
  const [invokerOpen, setInvokerOpen] = useState(false);
  const [invokerQuery, setInvokerQuery] = useState('');
  const invokerDropdown = useRef(null);
  const [refreshToken, setRefreshToken] = useState(0);

  useEffect(() => {
    let cancelled = false;
    fetchUsers().then((list) => !cancelled && setUsers(list)).catch(() => !cancelled && setUsers([]));
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!invokerOpen) return undefined;
    function onDocClick(event) {
      if (invokerDropdown.current && !invokerDropdown.current.contains(event.target)) setInvokerOpen(false);
    }
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [invokerOpen]);

  const usersById = useMemo(() => new Map((users || []).map((user) => [user.id, user])), [users]);
  const invokerName = useCallback((run) => {
    const user = usersById.get(run.invokedBy);
    return user?.displayName || user?.email || run.invokedBy || 'Unknown';
  }, [usersById]);
  const normalizedInvokerQuery = invokerQuery.trim().toLowerCase();
  const matchingUsers = useMemo(() => (users || []).filter((user) => !normalizedInvokerQuery
    || `${user.displayName} ${user.email} ${user.username}`.toLowerCase().includes(normalizedInvokerQuery)), [users, normalizedInvokerQuery]);

  useEffect(() => {
    const controller = new AbortController();
    let timer;
    (async () => {
      try {
        const merged = [];
        var before = null;
        for (let page = 0; page < MAX_PAGES; page++) {
          const batch = await fetchRunPage(before === null ? {} : { before });
          merged.push(...batch);
          if (batch.length < 2 * PAGE_SIZE) break;
          before = Math.min(...batch.map((run) => run.createdAt));
        }
        if (controller.signal.aborted) return;
        setRuns(merged);
        setHasMore(merged.length >= 2 * PAGE_SIZE * MAX_PAGES || merged.length > 0 && merged.length % (2 * PAGE_SIZE) === 0);
        setError(null);
        if (merged.some((run) => !terminalStatuses.has(run.runStatus))) {
          timer = window.setTimeout(() => setRefreshToken((value) => value + 1), 4000);
        }
      } catch (nextError) {
        if (!controller.signal.aborted) setError(nextError);
      }
    })();
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [refreshToken]);

  async function loadMore() {
    if (!runs?.length || loadingMore) return;
    setLoadingMore(true);
    try {
      const before = Math.min(...runs.map((run) => run.createdAt));
      const batch = await fetchRunPage({ before });
      setRuns((current) => [...current, ...batch]);
      setHasMore(batch.length >= 2 * PAGE_SIZE);
    } catch {
      setHasMore(false);
    } finally {
      setLoadingMore(false);
    }
  }

  const filteredRuns = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return (runs || []).filter((run) => {
      const runStatus = presentation(run).label.toLowerCase();
      return (status === 'all' || runStatus === status)
        && (invokerId === 'all' || run.invokedBy === invokerId)
        && (!normalizedQuery || `${runSearchText(run)} ${invokerName(run)}`.toLowerCase().includes(normalizedQuery));
    });
  }, [query, runs, status, invokerId, invokerName]);
  const selectedRun = filteredRuns.find((run) => run.runId === selectedRunId)
    || filteredRuns.find((run) => run.changeRequestId === selectedChangeRequestId)
    || filteredRuns[0]
    || null;
  const activeFilterCount = (status !== 'all' ? 1 : 0) + (invokerId !== 'all' ? 1 : 0);

  function selectRun(runId) {
    router.replace(`/history?runId=${encodeURIComponent(runId)}`, { scroll: false });
  }

  return (
    <div className="mx-auto flex max-w-[1400px] flex-1 flex-col min-h-0 lg:h-full">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <Overline>Analysis records</Overline>
          <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">Analysis history</h1>
          <p className="mt-2 text-[15px] text-muted">Track Clio Improve runs and open completed reviews.</p>
        </div>
        <Button onClick={() => router.push('/analyze')}><Play className="h-4 w-4" aria-hidden="true" />Run analysis</Button>
      </div>

      {!runs && !error ? <LoadingState label="Loading analysis history" /> : null}
      {error ? <ErrorState title="Could not load analysis history" error={error} onRetry={() => setRefreshToken((value) => value + 1)} /> : null}
      {runs?.length === 0 ? <UnavailableState title="No analysis runs yet" description="Run Clio Improve on a synchronized merge request to create the first record." /> : null}

      {runs?.length ? (
        <div className="grid min-h-0 flex-1 items-stretch gap-5 self-start lg:grid-cols-[minmax(0,1.45fr)_minmax(300px,0.75fr)] lg:self-stretch">
          {selectedRun?.groupId ? <GroupAnalysisDetail run={selectedRun} onRefresh={() => setRefreshToken((value) => value + 1)} /> : selectedRun ? <RunDetail run={selectedRun} onRunAgain={() => router.push(analysisHref(
            selectedRun.changeRequestId,
            selectedRun.reviewPersonas.map((persona) => persona.id),
            selectedRun.execution,
          ))} onOpenReview={() => {
            router.push(reviewHrefFromChangeRequestId(selectedRun.changeRequestId, selectedRun.runId));
          }} /> : null}
          <section className="flex min-h-0 flex-col border border-border bg-surface lg:overflow-hidden">
            <div className="flex shrink-0 items-center gap-3 border-b border-border p-4">
              <label className="relative min-w-0 flex-1">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-faint" aria-hidden="true" />
                <Input className="pl-9" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search run, project, title, group, or user" aria-label="Search analysis history" />
              </label>
              <div ref={invokerDropdown} className="relative">
                <Button variant="secondary" size="sm" aria-expanded={invokerOpen} aria-haspopup="dialog" onClick={() => { setInvokerOpen((open) => !open); setInvokerQuery(''); }}>
                  <SlidersHorizontal className="h-4 w-4" aria-hidden="true" />Filters{activeFilterCount ? ` · ${activeFilterCount}` : ''}
                </Button>
                {invokerOpen ? (
                  <div role="dialog" aria-label="Filters" className="absolute right-0 z-20 mt-1 w-72 border border-border-2 bg-surface-2 p-3 shadow-lg">
                    <p className="font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Status</p>
                    <select className="mt-1 mb-4 h-9 w-full border border-border bg-surface px-3 font-mono text-xs text-fg focus-visible:border-accent focus-visible:outline-none" value={status} onChange={(event) => setStatus(event.target.value)} aria-label="Filter analysis status">
                      <option value="all">All statuses</option>
                      <option value="running">Running</option>
                      <option value="completed">Completed</option>
                      <option value="failed">Failed</option>
                      <option value="partial">Partial</option>
                      <option value="stale">Stale</option>
                    </select>
                    <p className="font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Triggered by</p>
                    <input type="text" className="mt-1 h-9 w-full border border-border bg-surface px-3 text-[13px] text-fg placeholder:text-faint focus-visible:border-accent focus-visible:outline-none" placeholder="Search people…" value={invokerQuery} autoComplete="off" aria-label="Search users" onChange={(event) => setInvokerQuery(event.target.value)} />
                    <div className="mt-1 max-h-48 overflow-y-auto">
                      <button type="button" className={`block w-full px-2 py-1.5 text-left font-mono text-xs text-fg hover:bg-surface ${invokerId === 'all' ? 'bg-surface' : ''}`} onClick={() => setInvokerId('all')}>All users</button>
                      {matchingUsers.map((user) => (
                        <button key={user.id} type="button" className={`block w-full px-2 py-1.5 text-left hover:bg-surface ${invokerId === user.id ? 'bg-surface' : ''}`} onClick={() => setInvokerId(user.id)}>
                          <span className="block truncate text-sm text-fg">{user.displayName || user.username || user.email}</span>
                          <span className="block truncate font-mono text-[10px] text-muted">{user.email}</span>
                        </button>
                      ))}
                      {matchingUsers.length === 0 ? <p role="status" className="px-2 py-1.5 text-sm text-muted">No matching people.</p> : null}
                    </div>
                  </div>
                ) : null}
              </div>
            </div>
            <div className="grid shrink-0 grid-cols-[minmax(0,1fr)_110px] border-b border-border bg-surface-2 px-4 py-2 font-mono text-[10px] uppercase tracking-[0.08em] text-faint sm:grid-cols-[minmax(0,1fr)_95px_85px]">
              <span>Merge request / run</span><span>Status</span><span className="hidden sm:block">Started</span>
            </div>
            <div className="min-h-0 flex-1 divide-y divide-border lg:overflow-y-auto">
              {filteredRuns.map((run) => {
                const state = presentation(run);
                const started = new Date(run.createdAt);
                return (
                  <button key={run.runId} type="button" onClick={() => selectRun(run.runId)} className={`grid w-full grid-cols-[minmax(0,1fr)_110px] items-center border-l-[3px] px-4 py-3 text-left transition-colors hover:bg-surface-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring sm:grid-cols-[minmax(0,1fr)_95px_85px] ${state.border} ${selectedRun?.runId === run.runId ? 'bg-[var(--accent-soft)]' : ''}`}>
                    <span className="min-w-0"><span className="block truncate text-sm font-semibold text-fg">{runTitle(run)}</span><span className="mt-1 flex items-center gap-2 truncate font-mono text-[10px] text-faint"><span className="truncate">{run.runId}</span>{run.invokedBy ? <span className="text-muted">by {invokerName(run)}</span> : null}</span></span>
                    <Badge className="w-fit" variant={state.variant}>{state.label}</Badge>
                    <span className="hidden font-mono text-[11px] leading-tight text-muted sm:block">{started.toLocaleDateString()}<br />{started.toLocaleTimeString()}</span>
                  </button>
                );
              })}
              {filteredRuns.length === 0 ? <p className="p-8 text-center text-sm text-muted">No runs match these filters.</p> : null}
            </div>
            {hasMore ? (
              <div className="shrink-0 border-t border-border p-3">
                <Button className="w-full" variant="secondary" disabled={loadingMore} onClick={loadMore}>
                  {loadingMore ? 'Loading…' : 'Load more'}
                </Button>
              </div>
            ) : null}
          </section>
        </div>
      ) : null}
    </div>
  );
}

function RunDetail({ run, onOpenReview, onRunAgain }) {
  const state = presentation(run);
  const complete = terminalStatuses.has(run.runStatus);
  const publishedFindings = run.findings.filter((finding) => finding.publicationStatus === 'posted').length;
  const pipeline = run.pipelineUrl
    ? <a href={run.pipelineUrl} className="text-accent-ink hover:underline" target="_blank" rel="noreferrer">{run.pipelineId} ↗</a>
    : (run.pipelineId || 'Starting…');
  return (
    <AnalysisRunDetail
      title={mergeRequestLabel(run)}
      state={state}
      progressLabel="Findings published"
      progressValue={publishedFindings}
      progressMax={run.findings.length}
      personas={run.reviewPersonas}
      usage={run.usage}
      metadata={[
        ['Type', 'Single MR'],
        ['Run ID', run.runId],
        ['Pipeline', pipeline],
        ['Harness', run.execution?.harness || 'Deployment default'],
        ['Model', run.execution?.model || 'Deployment default'],
        ['Scope', '1 MR'],
        ['Personas', `${run.completedPersonas}/${run.personaIds.length} completed`],
        ['Review state', run.reviewState.replaceAll('_', ' ')],
        ['Started', new Date(run.createdAt).toLocaleString()],
        ['Updated', new Date(run.updatedAt).toLocaleString()],
      ]}
      actions={<>
        <Button className="w-full" variant={complete ? 'primary' : 'secondary'} onClick={onOpenReview}>{complete ? 'Open review' : <><RefreshCw className="h-4 w-4" aria-hidden="true" />View live review</>}</Button>
        {complete ? <Button className="w-full" variant="secondary" onClick={onRunAgain}><Play className="h-4 w-4" aria-hidden="true" />{state.label === 'Failed' ? 'Retry analysis' : 'Run analysis again'}</Button> : null}
      </>}
    >
      {run.outcomeDetail ? <p className={`mt-4 border border-border bg-surface-2 p-3 text-sm leading-5 ${state.variant === 'error' ? 'text-bad' : 'text-muted'}`}>{run.outcomeDetail}</p> : null}
      <AnalysisFindings findings={run.findings} personas={run.reviewPersonas} contextForFinding={() => mergeRequestLabel(run)} />
    </AnalysisRunDetail>
  );
}

export default function HistoryPage() {
  return <Suspense fallback={<LoadingState label="Loading history" />}><HistoryContent /></Suspense>;
}
