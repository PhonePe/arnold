'use client';

import React, { Suspense, useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Play, RefreshCw, Search } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { ErrorState } from '@/components/ui/ErrorState';
import { Input } from '@/components/ui/Input';
import { LoadingState } from '@/components/ui/LoadingState';
import { Overline } from '@/components/ui/Overline';
import { ProgressBar } from '@/components/ui/ProgressBar';
import { UnavailableState } from '@/components/ui/UnavailableState';
import { fetchAnalyses } from '@/lib/api';
import { parseChangeRequestId } from '@/lib/contracts';
import { analysisHref } from '@/lib/analysis-navigation.mjs';

const terminalStatuses = new Set(['completed', 'failed']);

function presentation(run) {
  if (run.runStatus === 'failed' || run.analysisOutcome === 'FAILED') {
    return { label: 'Failed', variant: 'error', border: 'border-l-bad', progress: 'error' };
  }
  if (run.runStatus === 'completed') {
    return { label: 'Completed', variant: 'success', border: 'border-l-ok', progress: 'success' };
  }
  return { label: 'Running', variant: 'warning', border: 'border-l-tertiary', progress: 'tertiary' };
}

function mergeRequestLabel(changeRequestId) {
  const { repositoryId, number } = parseChangeRequestId(changeRequestId);
  return `Project ${repositoryId} · MR !${number}`;
}

function HistoryContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const selectedRunId = searchParams.get('runId');
  const selectedChangeRequestId = searchParams.get('changeRequestId');
  const [runs, setRuns] = useState(null);
  const [error, setError] = useState(null);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('all');
  const [refreshToken, setRefreshToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    let timer;
    fetchAnalyses({ signal: controller.signal })
      .then((nextRuns) => {
        setRuns(nextRuns);
        setError(null);
        if (nextRuns.some((run) => !terminalStatuses.has(run.runStatus))) {
          timer = window.setTimeout(() => setRefreshToken((value) => value + 1), 4000);
        }
      })
      .catch((nextError) => !controller.signal.aborted && setError(nextError));
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [refreshToken]);

  const filteredRuns = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return (runs || []).filter((run) => {
      const runStatus = presentation(run).label.toLowerCase();
      return (status === 'all' || runStatus === status)
        && (!normalizedQuery || `${run.runId} ${run.changeRequestId} ${run.pipelineId || ''}`.toLowerCase().includes(normalizedQuery));
    });
  }, [query, runs, status]);
  const selectedRun = filteredRuns.find((run) => run.runId === selectedRunId)
    || filteredRuns.find((run) => run.changeRequestId === selectedChangeRequestId)
    || filteredRuns[0]
    || null;
  const completedCount = runs?.filter((run) => presentation(run).label === 'Completed').length || 0;
  const runningCount = runs?.filter((run) => presentation(run).label === 'Running').length || 0;
  const failedCount = runs?.filter((run) => presentation(run).label === 'Failed').length || 0;

  function selectRun(runId) {
    router.replace(`/history?runId=${encodeURIComponent(runId)}`, { scroll: false });
  }

  return (
    <div className="mx-auto max-w-[1400px]">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <Overline>Analysis records</Overline>
          <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">Analysis history</h1>
          <p className="mt-2 text-[15px] text-muted">Track Clio Improve runs and open completed reviews.</p>
        </div>
        <Button onClick={() => router.push('/analyze')}><Play className="h-4 w-4" aria-hidden="true" />Run analysis</Button>
      </div>

      {runs ? (
        <div className="mb-5 grid gap-px border border-border bg-border sm:grid-cols-3">
          {[['Completed', completedCount, 'text-ok'], ['Running', runningCount, 'text-tertiary-ink'], ['Failed', failedCount, 'text-bad']].map(([label, value, color]) => (
            <div key={label} className="bg-surface p-4">
              <p className="font-mono text-[10px] uppercase tracking-[0.1em] text-faint">{label}</p>
              <p className={`mt-1 font-display text-3xl font-bold ${color}`}>{value}</p>
            </div>
          ))}
        </div>
      ) : null}

      {!runs && !error ? <LoadingState label="Loading analysis history" /> : null}
      {error ? <ErrorState title="Could not load analysis history" error={error} onRetry={() => setRefreshToken((value) => value + 1)} /> : null}
      {runs?.length === 0 ? <UnavailableState title="No analysis runs yet" description="Run Clio Improve on a synchronized merge request to create the first record." /> : null}

      {runs?.length ? (
        <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1.45fr)_minmax(320px,0.75fr)]">
          <section className="border border-border bg-surface">
            <div className="grid gap-3 border-b border-border p-4 sm:grid-cols-[minmax(0,1fr)_160px]">
              <label className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-faint" aria-hidden="true" />
                <Input className="pl-9" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search run, MR, or pipeline" aria-label="Search analysis history" />
              </label>
              <select className="h-9 border border-border-2 bg-surface-2 px-3 font-mono text-xs text-fg focus-visible:border-accent focus-visible:outline-none" value={status} onChange={(event) => setStatus(event.target.value)} aria-label="Filter analysis status">
                <option value="all">All statuses</option>
                <option value="running">Running</option>
                <option value="completed">Completed</option>
                <option value="failed">Failed</option>
              </select>
            </div>
            <div className="grid grid-cols-[minmax(0,1fr)_110px] border-b border-border bg-surface-2 px-4 py-2 font-mono text-[10px] uppercase tracking-[0.08em] text-faint sm:grid-cols-[minmax(0,1fr)_120px_150px]">
              <span>Merge request / run</span><span>Status</span><span className="hidden sm:block">Started</span>
            </div>
            <div className="divide-y divide-border">
              {filteredRuns.map((run) => {
                const state = presentation(run);
                return (
                  <button key={run.runId} type="button" onClick={() => selectRun(run.runId)} className={`grid w-full grid-cols-[minmax(0,1fr)_110px] items-center border-l-[3px] px-4 py-3 text-left transition-colors hover:bg-surface-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring sm:grid-cols-[minmax(0,1fr)_120px_150px] ${state.border} ${selectedRun?.runId === run.runId ? 'bg-[var(--accent-soft)]' : ''}`}>
                    <span className="min-w-0"><span className="block truncate text-sm font-semibold text-fg">{mergeRequestLabel(run.changeRequestId)}</span><span className="mt-1 block truncate font-mono text-[10px] text-faint">{run.runId}</span></span>
                    <Badge className="w-fit" variant={state.variant}>{state.label}</Badge>
                    <span className="hidden font-mono text-[11px] text-muted sm:block">{new Date(run.createdAt).toLocaleString()}</span>
                  </button>
                );
              })}
              {filteredRuns.length === 0 ? <p className="p-8 text-center text-sm text-muted">No runs match these filters.</p> : null}
            </div>
          </section>

          {selectedRun ? <RunDetail run={selectedRun} onRunAgain={() => router.push(analysisHref(
            selectedRun.changeRequestId,
            selectedRun.reviewPersonas.map((persona) => persona.id),
          ))} onOpenReview={() => {
            const reference = parseChangeRequestId(selectedRun.changeRequestId);
            router.push(`/review?provider=${reference.provider}&repositoryId=${reference.repositoryId}&number=${reference.number}&runId=${encodeURIComponent(selectedRun.runId)}`);
          }} /> : null}
        </div>
      ) : null}
    </div>
  );
}

function RunDetail({ run, onOpenReview, onRunAgain }) {
  const state = presentation(run);
  const complete = terminalStatuses.has(run.runStatus);
  return (
    <aside className={`border border-border border-l-[3px] bg-surface p-5 lg:sticky lg:top-20 ${state.border}`}>
      <div className="flex items-start justify-between gap-3">
        <div><Overline tone="faint">Selected run</Overline><h2 className="mt-1 font-display text-xl font-bold text-fg">{mergeRequestLabel(run.changeRequestId)}</h2></div>
        <Badge variant={state.variant}>{state.label}</Badge>
      </div>
      <div className="mt-5"><div className="mb-2 flex justify-between font-mono text-[11px] text-muted"><span>Improve persona</span><span>{run.completedPersonas}/{run.personaIds.length}</span></div><ProgressBar value={run.completedPersonas} max={Math.max(run.personaIds.length, 1)} color={state.progress} /></div>
      {run.reviewPersonas.length ? <div className="mt-4"><p className="mb-2 font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Supplemental personas</p><div className="flex flex-wrap gap-2">{run.reviewPersonas.map((persona) => <Badge key={`${persona.id}:${persona.version}`} variant="info">{persona.name}</Badge>)}</div></div> : null}
      <dl className="mt-5 divide-y divide-border border-y border-border">
        {[['Run ID', run.runId], ['Pipeline', run.pipelineId || 'Starting…'], ['Review state', run.reviewState.replaceAll('_', ' ')], ['Started', new Date(run.createdAt).toLocaleString()], ['Updated', new Date(run.updatedAt).toLocaleString()]].map(([label, value]) => (
          <div key={label} className="grid grid-cols-[90px_minmax(0,1fr)] gap-3 py-3"><dt className="font-mono text-[10px] uppercase tracking-[0.06em] text-faint">{label}</dt><dd className="break-all font-mono text-xs text-fg">{value}</dd></div>
        ))}
      </dl>
      {run.outcomeDetail ? <p className={`mt-4 border border-border bg-surface-2 p-3 text-sm leading-5 ${state.variant === 'error' ? 'text-bad' : 'text-muted'}`}>{run.outcomeDetail}</p> : null}
      <div className="mt-5 grid gap-2">
        <Button className="w-full" variant={complete ? 'primary' : 'secondary'} onClick={onOpenReview}>{complete ? 'Open review' : <><RefreshCw className="h-4 w-4" aria-hidden="true" />View live review</>}</Button>
        {complete ? <Button className="w-full" variant="secondary" onClick={onRunAgain}><Play className="h-4 w-4" aria-hidden="true" />{state.label === 'Failed' ? 'Retry analysis' : 'Run again'}</Button> : null}
      </div>
    </aside>
  );
}

export default function HistoryPage() {
  return <Suspense fallback={<LoadingState label="Loading history" />}><HistoryContent /></Suspense>;
}
