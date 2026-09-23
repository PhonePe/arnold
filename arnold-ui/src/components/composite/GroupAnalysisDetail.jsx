'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Play, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ErrorState } from '@/components/ui/ErrorState';
import { AnalysisFindings } from '@/components/composite/AnalysisFindings';
import { AnalysisRunDetail } from '@/components/composite/AnalysisRunDetail';
import { fetchGroupAnalysis, retryGroupPublication } from '@/lib/api';

function presentation(runStatus) {
  if (runStatus === 'partial') return { label: 'Partial', variant: 'warning', border: 'border-l-tertiary', progress: 'tertiary' };
  if (runStatus === 'stale') return { label: 'Stale', variant: 'warning', border: 'border-l-tertiary', progress: 'tertiary' };
  if (runStatus === 'failed') return { label: 'Failed', variant: 'error', border: 'border-l-bad', progress: 'error' };
  if (runStatus === 'completed') return { label: 'Completed', variant: 'success', border: 'border-l-ok', progress: 'success' };
  return { label: 'Running', variant: 'warning', border: 'border-l-tertiary', progress: 'tertiary' };
}

export function GroupAnalysisDetail({ run, onRefresh, className = '', ...props }) {
  const router = useRouter();
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState(null);
  const [retrying, setRetrying] = useState(false);
  const current = detail?.runId === run.runId && detail.updatedAt >= run.updatedAt ? detail : run;
  const finished = !['queued', 'running'].includes(current.runStatus);
  const state = presentation(current.runStatus);
  const publishedFindings = current.findings.filter((finding) => finding.publicationStatus === 'posted').length;
  const canRetry = ['partial', 'failed'].includes(current.runStatus)
    && current.findings.some((finding) => finding.publicationStatus !== 'posted') && !current.stale;

  useEffect(() => {
    const controller = new AbortController();
    fetchGroupAnalysis(run.runId, { signal: controller.signal }).then((value) => {
      setDetail(value);
      setError(null);
    }).catch((nextError) => !controller.signal.aborted && setError(nextError));
    return () => controller.abort();
  }, [run.runId, run.updatedAt]);

  async function retry() {
    setRetrying(true);
    setError(null);
    try {
      setDetail(await retryGroupPublication(run.runId));
      onRefresh();
    } catch (nextError) {
      setError(nextError);
    } finally {
      setRetrying(false);
    }
  }

  const pipeline = current.pipelineUrl
    ? <a href={current.pipelineUrl} className="text-accent-ink hover:underline" target="_blank" rel="noreferrer">{current.pipelineId} ↗</a>
    : (current.pipelineId || 'Starting…');

  return (
    <AnalysisRunDetail
      title={current.groupName}
      state={state}
      progressLabel="Findings published"
      progressValue={publishedFindings}
      progressMax={current.findings.length}
      personas={current.reviewPersonas}
      metadata={[
        ['Type', 'MR group'],
        ['Run ID', current.runId],
        ['Pipeline', pipeline],
        ['Scope', `${current.members.length} MRs`],
        ['Started', new Date(run.createdAt).toLocaleString()],
        ['Updated', new Date(current.updatedAt).toLocaleString()],
      ]}
      notice={current.stale ? <p role="status" className="mt-4 border border-warn p-3 text-sm text-warn">These results are stale or could not be verified against the current group. They describe the saved revisions; start a new analysis for the current group.</p> : null}
      actions={<>
        {finished ? <Button className="w-full" variant="primary" onClick={() => router.push(`/analyze?groupId=${encodeURIComponent(current.groupId)}`)}><Play className="h-4 w-4" aria-hidden="true" />Run analysis again</Button> : null}
        {canRetry ? <Button className="w-full" variant="secondary" disabled={retrying} onClick={retry}><RefreshCw className="h-4 w-4" aria-hidden="true" />{retrying ? 'Starting…' : 'Retry publication'}</Button> : null}
      </>}
      className={className}
      {...props}
    >
      <div className="mt-5">
        <p className="mb-2 font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Merge requests</p>
        <div className="divide-y divide-border border-y border-border">{current.members.map((member) => <div key={member.mergeRequestId} className="py-3">
          <a className="text-sm font-semibold text-accent-ink" href={member.webUrl} target="_blank" rel="noreferrer">{member.repositoryPath} !{member.iid}</a>
          <p className="mt-1 text-sm text-fg">{member.title}</p>
          <p className="mt-1 font-mono text-xs text-muted">{member.headSha.slice(0, 8)} · {current.memberStatuses[member.mergeRequestId] || 'queued'}</p>
        </div>)}</div>
      </div>

      {current.detail ? <p className="mt-4 text-sm text-muted" role="status">{current.detail}</p> : null}

      <AnalysisFindings findings={current.findings} contextForFinding={(finding) => {
        const member = current.members.find((item) => item.mergeRequestId === finding.mergeRequestId);
        return `${member.repositoryPath} !${member.iid}`;
      }} />

      {error ? <ErrorState className="mt-4" title="Could not update group analysis" error={error} /> : null}
    </AnalysisRunDetail>
  );
}
