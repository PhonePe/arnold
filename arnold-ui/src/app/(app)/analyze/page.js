'use client';

import React, { Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Overline } from '@/components/ui/Overline';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { LoadingState } from '@/components/ui/LoadingState';
import { ErrorState } from '@/components/ui/ErrorState';
import { UnavailableState } from '@/components/ui/UnavailableState';
import { ProgressBar } from '@/components/ui/ProgressBar';
import { configuredPersonas, providerCapabilities } from '@/lib/capabilities';
import { fetchAnalysis, resolveMergeRequest, startAnalysis } from '@/lib/api';

const delayForAttempt = (attempt) => attempt <= 5 ? 2000 : attempt <= 20 ? 5000 : 10000;

function AnalyzeContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialRunId = searchParams.get('runId');
  const [url, setUrl] = useState('');
  const [mergeRequest, setMergeRequest] = useState(null);
  const [selected, setSelected] = useState([]);
  const [runId, setRunId] = useState(initialRunId);
  const [analysis, setAnalysis] = useState(null);
  const [status, setStatus] = useState(initialRunId ? 'polling' : 'idle');
  const [error, setError] = useState(null);
  const [pollToken, setPollToken] = useState(0);

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
        if (nextAnalysis.runStatus === 'completed') {
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

  async function resolve(event) {
    event.preventDefault();
    if (!url.trim()) return;
    setStatus('resolving');
    setError(null);
    try {
      setMergeRequest(await resolveMergeRequest(url.trim()));
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
      const nextAnalysis = await startAnalysis({ mergeRequestId: mergeRequest.id, personaIds: selected });
      setAnalysis(nextAnalysis);
      setRunId(nextAnalysis.runId);
      setStatus(nextAnalysis.runStatus === 'running' ? 'polling' : nextAnalysis.runStatus === 'completed' ? 'completed' : 'unknown');
      router.replace(`/analyze?runId=${encodeURIComponent(nextAnalysis.runId)}`);
    } catch (nextError) {
      setError(nextError);
      const unavailable = nextError.errorCode === 4000 || /dispatcher is not configured/i.test(nextError.message);
      setStatus(unavailable ? 'dispatcher-unavailable' : 'start-error');
    }
  }

  return (
    <div className="mx-auto max-w-[1000px]">
      <div className="mb-6"><Overline>Single merge request</Overline><h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold text-fg">Run analysis</h1><p className="mt-2 text-[15px] text-muted">Resolve one synchronized merge request and track its backend analysis run.</p></div>
      {!runId ? (
        <form onSubmit={resolve} className="flex flex-col gap-3 border border-border bg-surface p-5 sm:flex-row">
          <Input value={url} onChange={(event) => setUrl(event.target.value)} placeholder="Exact synchronized GitLab MR URL" aria-label="Merge request URL" disabled={status === 'resolving'} />
          <Button type="submit" disabled={!url.trim() || status === 'resolving'}>{status === 'resolving' ? 'Resolving…' : 'Resolve MR'}</Button>
        </form>
      ) : null}
      {status === 'unsynchronized' ? <ErrorState className="mt-4" title="Merge request not synchronized" error={{ message: 'This merge request has not been synchronized into Arnold yet.' }} /> : null}
      {status === 'resolve-error' ? <ErrorState className="mt-4" title="Could not resolve merge request" error={error} /> : null}

      {mergeRequest ? (
        <section className="mt-5 border border-border bg-surface p-5">
          <Overline tone="faint">Authoritative metadata</Overline>
          <h2 className="mt-2 font-display text-xl font-bold text-fg">{mergeRequest.title}</h2>
          <p className="mt-2 font-mono text-xs text-muted">{mergeRequest.repositoryPath} · !{mergeRequest.changeNumber} · {mergeRequest.sourceBranch} → {mergeRequest.targetBranch}</p>
          <div className="mt-5 border-t border-border pt-4">
            <h3 className="font-display text-lg font-bold text-fg">Configured personas</h3>
            {configuredPersonas.length ? <div className="mt-3 grid gap-2 sm:grid-cols-2">{configuredPersonas.map((persona) => <label key={persona.id} className="flex items-center gap-3 border border-border bg-surface-2 p-3 text-sm text-fg"><input type="checkbox" checked={selected.includes(persona.id)} onChange={(event) => setSelected((current) => event.target.checked ? [...current, persona.id] : current.filter((id) => id !== persona.id))} /><span>{persona.name}</span><span className="ml-auto font-mono text-[10px] text-muted">{persona.id}</span></label>)}</div> : <p className="mt-2 text-sm text-muted">No analysis personas are configured.</p>}
            {!providerCapabilities.GITLAB.canStartAnalysis ? <UnavailableState className="mt-4" title="Analysis execution unavailable" description="Analysis execution is disabled until a dispatcher and configured personas are available." /> : null}
            <Button className="mt-4" onClick={start} disabled={!providerCapabilities.GITLAB.canStartAnalysis || selected.length === 0 || status === 'starting'}>{status === 'starting' ? 'Starting…' : 'Start analysis'}</Button>
          </div>
        </section>
      ) : null}

      {status === 'polling' && !analysis ? <LoadingState className="mt-5" label="Loading analysis run" /> : null}
      {analysis ? <section className="mt-5 border border-border bg-surface p-5"><div className="flex items-center justify-between gap-4"><h2 className="font-display text-xl font-bold text-fg">Analysis {analysis.runStatus}</h2><span className="font-mono text-xs text-muted">{analysis.runId}</span></div><p className="mt-3 font-mono text-xs text-muted">{analysis.completedPersonas} / {analysis.personaIds.length} personas completed</p><ProgressBar className="mt-2" value={analysis.completedPersonas} max={Math.max(analysis.personaIds.length, 1)} /></section> : null}
      {status === 'dispatcher-unavailable' ? <UnavailableState className="mt-5" title="Analysis execution is not configured" description="The run may have been created before dispatch failed. Do not retry automatically; check with an operator before starting again." /> : null}
      {status === 'start-error' ? <ErrorState className="mt-5" title="Could not start analysis" error={error} /> : null}
      {status === 'interrupted' ? <ErrorState className="mt-5" title="Analysis polling interrupted" error={error} onRetry={() => { setError(null); setStatus('polling'); setPollToken((value) => value + 1); }} /> : null}
      {status === 'not-found' ? <ErrorState className="mt-5" title="Analysis run not found" error={error} /> : null}
      {status === 'unknown' ? <UnavailableState className="mt-5" title="Unknown analysis status" description={`Arnold returned ${analysis?.runStatus || 'an unknown status'}. Polling has stopped.`} /> : null}
    </div>
  );
}

export default function AnalyzePage() {
  return <Suspense fallback={<LoadingState label="Loading analysis" />}><AnalyzeContent /></Suspense>;
}
