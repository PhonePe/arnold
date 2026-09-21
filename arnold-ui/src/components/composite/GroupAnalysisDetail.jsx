'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { ErrorState } from '@/components/ui/ErrorState';
import { Markdown } from '@/components/ui/Markdown';
import { fetchGroupAnalysis, retryGroupPublication } from '@/lib/api';

export function GroupAnalysisDetail({ run, onRefresh, className = '', ...props }) {
  const router = useRouter();
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState(null);
  const [retrying, setRetrying] = useState(false);
  const current = detail?.runId === run.runId && detail.updatedAt >= run.updatedAt ? detail : run;
  const finished = !['queued', 'running'].includes(current.runStatus);

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

  return <aside className={`grid gap-4 border border-border bg-surface p-5 ${className}`} {...props}>
    <div className="flex items-start justify-between gap-3"><h2 className="font-display text-xl font-bold text-fg">{current.groupName}</h2><Badge variant={current.runStatus === 'completed' ? 'success' : 'warning'}>{current.runStatus}</Badge></div>
    <p className="break-all font-mono text-xs text-muted">Run {current.runId} · Pipeline {current.pipelineId || 'Starting…'}</p>
    {current.stale ? <p role="status" className="border border-warn p-3 text-sm text-warn">These results are stale or could not be verified against the current group. They describe the saved revisions; start a new analysis for the current group.</p> : null}
    <div className="divide-y divide-border border-y border-border">{current.members.map((member) => <div key={member.mergeRequestId} className="py-3">
      <a className="text-sm font-semibold text-accent-ink" href={member.webUrl} target="_blank" rel="noreferrer">{member.repositoryPath} !{member.iid}</a>
      <p className="mt-1 text-sm text-fg">{member.title}</p>
      <p className="mt-1 font-mono text-xs text-muted">{member.headSha.slice(0, 8)} · {current.memberStatuses[member.mergeRequestId] || 'queued'}</p>
    </div>)}</div>
    {current.summary ? <Markdown>{current.summary}</Markdown> : null}
    {current.detail ? <p className="text-sm text-muted" role="status">{current.detail}</p> : null}
    <p className="font-mono text-xs text-muted">{current.findings.filter((finding) => finding.publicationStatus === 'posted').length}/{current.findings.length} findings published</p>
    {current.findings.map((finding) => {
      const member = current.members.find((item) => item.mergeRequestId === finding.mergeRequestId);
      return <section key={finding.id} className="min-w-0 border border-border bg-surface-2 p-3">
        <p className="mb-2 break-all font-mono text-xs text-muted">{member.repositoryPath} !{member.iid} · {finding.filePath || 'General discussion'}{finding.newLine ? `:${finding.newLine}` : ''}</p>
        <Markdown>{finding.body}</Markdown>
        <div className="mt-2 flex items-center gap-3"><Badge variant={finding.publicationStatus === 'posted' ? 'success' : 'warning'}>{finding.publicationStatus === 'unknown' ? 'Unconfirmed' : finding.publicationStatus}</Badge>{finding.commentUrl ? <a href={finding.commentUrl} className="text-sm text-accent-ink" target="_blank" rel="noreferrer">View comment</a> : null}</div>
      </section>;
    })}
    {finished ? <Button onClick={() => router.push(`/analyze?groupId=${encodeURIComponent(current.groupId)}`)}>Run analysis again</Button> : null}
    {['partial', 'failed'].includes(current.runStatus) && current.summary && current.findings.some((finding) => finding.publicationStatus !== 'posted') && !current.stale ? <Button variant="secondary" disabled={retrying} onClick={retry}>{retrying ? 'Starting…' : 'Retry publication'}</Button> : null}
    {error ? <ErrorState title="Could not update group analysis" error={error} /> : null}
  </aside>;
}
