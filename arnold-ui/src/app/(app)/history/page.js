'use client';

import React, { Suspense, useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { Overline } from '@/components/ui/Overline';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { UnavailableState } from '@/components/ui/UnavailableState';
import { ProgressBar } from '@/components/ui/ProgressBar';
import { fetchAnalysis } from '@/lib/api';

function HistoryContent() {
  const runId = useSearchParams().get('runId');
  const [analysis, setAnalysis] = useState(null);
  const [error, setError] = useState(null);
  useEffect(() => {
    if (!runId) return undefined;
    const controller = new AbortController();
    fetchAnalysis(runId, { signal: controller.signal }).then(setAnalysis).catch((nextError) => !controller.signal.aborted && setError(nextError));
    return () => controller.abort();
  }, [runId]);
  return <div className="mx-auto max-w-[1000px]"><div className="mb-6"><Overline>Analysis records</Overline><h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold text-fg">Analysis history</h1></div>{!runId ? <UnavailableState title="Analysis history listing unavailable" description="Arnold has no analysis list endpoint. Open a known run by adding its runId to this route." /> : null}{runId && !analysis && !error ? <LoadingState label="Loading analysis run" /> : null}{error ? <ErrorState title={error.status === 404 ? 'Analysis run not found' : 'Could not load analysis run'} error={error} /> : null}{analysis ? <section className="border border-border bg-surface p-5"><div className="flex flex-wrap items-center justify-between gap-3"><h2 className="font-display text-xl font-bold text-fg">{analysis.runStatus}</h2><span className="font-mono text-xs text-muted">{analysis.runId}</span></div><p className="mt-3 font-mono text-xs text-muted">{analysis.changeRequestId} · {analysis.reviewState}</p><p className="mt-4 font-mono text-xs text-muted">{analysis.completedPersonas}/{analysis.personaIds.length} personas completed</p><ProgressBar className="mt-2" value={analysis.completedPersonas} max={Math.max(analysis.personaIds.length, 1)} /><p className="mt-4 font-mono text-[10px] text-muted">Created {new Date(analysis.createdAt).toLocaleString()} · Updated {new Date(analysis.updatedAt).toLocaleString()}</p></section> : null}</div>;
}

export default function HistoryPage() {
  return <Suspense fallback={<LoadingState label="Loading history" />}><HistoryContent /></Suspense>;
}
