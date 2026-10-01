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
import { UnavailableState } from '@/components/ui/UnavailableState';
import { fetchAnalyses, fetchGroupAnalyses } from '@/lib/api';
import { AnalysisFindings } from '@/components/composite/AnalysisFindings';
import { AnalysisRunDetail } from '@/components/composite/AnalysisRunDetail';
import { GroupAnalysisDetail } from '@/components/composite/GroupAnalysisDetail';
import { parseChangeRequestId } from '@/lib/contracts';
import { analysisHref } from '@/lib/analysis-navigation.mjs';

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
    Promise.all([fetchAnalyses({ signal: controller.signal }), fetchGroupAnalyses({ signal: controller.signal })])
      .then(([singleRuns, groupRuns]) => {
        const nextRuns = [...singleRuns, ...groupRuns].sort((a, b) => b.createdAt - a.createdAt);
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
        && (!normalizedQuery || `${run.runId} ${run.groupName || run.changeRequestId} ${run.pipelineId || ''}`.toLowerCase().includes(normalizedQuery));
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
                <option value="partial">Partial</option>
                <option value="stale">Stale</option>
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
                    <span className="min-w-0"><span className="block truncate text-sm font-semibold text-fg">{run.groupId ? `${run.groupName} · ${run.members.length} MRs` : mergeRequestLabel(run.changeRequestId)}</span><span className="mt-1 block truncate font-mono text-[10px] text-faint">{run.runId}</span></span>
                    <Badge className="w-fit" variant={state.variant}>{state.label}</Badge>
                    <span className="hidden font-mono text-[11px] text-muted sm:block">{new Date(run.createdAt).toLocaleString()}</span>
                  </button>
                );
              })}
              {filteredRuns.length === 0 ? <p className="p-8 text-center text-sm text-muted">No runs match these filters.</p> : null}
            </div>
          </section>

          {selectedRun?.groupId ? <GroupAnalysisDetail run={selectedRun} onRefresh={() => setRefreshToken((value) => value + 1)} /> : selectedRun ? <RunDetail run={selectedRun} onRunAgain={() => router.push(analysisHref(
            selectedRun.changeRequestId,
            selectedRun.reviewPersonas.map((persona) => persona.id),
            selectedRun.execution,
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
  const publishedFindings = run.findings.filter((finding) => finding.publicationStatus === 'posted').length;
  const pipeline = run.pipelineUrl
    ? <a href={run.pipelineUrl} className="text-accent-ink hover:underline" target="_blank" rel="noreferrer">{run.pipelineId} ↗</a>
    : (run.pipelineId || 'Starting…');
  return (
    <AnalysisRunDetail
      title={mergeRequestLabel(run.changeRequestId)}
      state={state}
      progressLabel="Findings published"
      progressValue={publishedFindings}
      progressMax={run.findings.length}
      personas={run.reviewPersonas}
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
      <AnalysisFindings findings={run.findings} contextForFinding={() => mergeRequestLabel(run.changeRequestId)} />
    </AnalysisRunDetail>
  );
}

export default function HistoryPage() {
  return <Suspense fallback={<LoadingState label="Loading history" />}><HistoryContent /></Suspense>;
}
