'use client';

import React, { Suspense, useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Overline } from '@/components/ui/Overline';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { StaleDataNotice } from '@/components/ui/StaleDataNotice';
import { UnavailableState } from '@/components/ui/UnavailableState';
import { DiscussionList } from '@/components/composite/DiscussionList';
import { DiscussionComposer } from '@/components/composite/DiscussionComposer';
import { fetchAnalysis, fetchDiscussions, fetchMergeRequest } from '@/lib/api';
import { providerCapabilities } from '@/lib/capabilities';

function MetadataRow({ label, children }) {
  return <div className="grid gap-1 border-t border-border py-3 sm:grid-cols-[180px_1fr]"><dt className="font-mono text-[10px] uppercase text-muted">{label}</dt><dd className="text-sm text-fg">{children || 'Not provided'}</dd></div>;
}

function ReviewContent() {
  const params = useSearchParams();
  const provider = (params.get('provider') || 'GITLAB').toUpperCase();
  const repositoryId = params.get('repositoryId') || params.get('projectId');
  const number = params.get('number') || params.get('iid');
  const runId = params.get('runId');
  const validIdentity = repositoryId && /^\d+$/.test(number || '') && Number(number) > 0;
  const [mergeRequest, setMergeRequest] = useState(null);
  const [mrError, setMrError] = useState(null);
  const [discussions, setDiscussions] = useState(null);
  const [discussionError, setDiscussionError] = useState(null);
  const [analysis, setAnalysis] = useState(null);
  const [analysisError, setAnalysisError] = useState(null);

  const reloadDiscussions = useCallback(async () => {
    if (!validIdentity || provider !== 'GITLAB') return;
    setDiscussionError(null);
    try { setDiscussions(await fetchDiscussions(repositoryId, number)); } catch (error) { setDiscussionError(error); }
  }, [number, provider, repositoryId, validIdentity]);

  useEffect(() => {
    if (!validIdentity || provider !== 'GITLAB') return undefined;
    const controller = new AbortController();
    fetchMergeRequest(repositoryId, number, { provider, signal: controller.signal }).then(setMergeRequest).catch((error) => !controller.signal.aborted && setMrError(error));
    fetchDiscussions(repositoryId, number, { signal: controller.signal }).then(setDiscussions).catch((error) => !controller.signal.aborted && setDiscussionError(error));
    if (runId) fetchAnalysis(runId, { signal: controller.signal }).then(setAnalysis).catch((error) => !controller.signal.aborted && setAnalysisError(error));
    return () => controller.abort();
  }, [number, provider, repositoryId, runId, validIdentity]);

  if (!validIdentity) return <EmptyState title="Invalid review route" description="Choose a synchronized merge request from your queue." action={<Link href="/queue"><Button>Open queue</Button></Link>} />;
  if (provider !== 'GITLAB') return <UnavailableState title={`${provider} reviews unavailable`} description="Only the GitLab provider is operational." />;

  return (
    <div className="mx-auto max-w-[1200px]">
      <div className="mb-6"><Overline>GitLab merge request</Overline><h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold text-fg">Review workspace</h1></div>
      {!mergeRequest && !mrError ? <LoadingState label="Loading merge request" /> : null}
      {mrError ? <ErrorState title="Could not load merge request" error={mrError} /> : null}
      {mergeRequest ? <section className="border border-border bg-surface p-5"><div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="font-display text-2xl font-bold text-fg">{mergeRequest.title}</h2><p className="mt-1 font-mono text-xs text-muted">{mergeRequest.repositoryPath} · !{mergeRequest.changeNumber}</p></div><a className="font-mono text-xs text-accent-ink hover:underline" href={mergeRequest.webUrl} target="_blank" rel="noreferrer">Open in GitLab ↗</a></div><StaleDataNotice className="mt-4" syncedAt={mergeRequest.lastSyncedAt} /><dl className="mt-4"><MetadataRow label="Branches">{mergeRequest.sourceBranch} → {mergeRequest.targetBranch}</MetadataRow><MetadataRow label="Provider state">{mergeRequest.draft ? 'Draft · ' : ''}{mergeRequest.state}</MetadataRow><MetadataRow label="Provider merge status">{mergeRequest.mergeStatus}</MetadataRow><MetadataRow label="Labels">{mergeRequest.labels.join(', ') || 'None'}</MetadataRow><MetadataRow label="Author ID">{mergeRequest.authorId}</MetadataRow><MetadataRow label="Assignee IDs">{mergeRequest.assigneeIds.join(', ') || 'None'}</MetadataRow><MetadataRow label="Reviewer IDs">{mergeRequest.reviewerIds.join(', ') || 'None'}</MetadataRow><MetadataRow label="Provider updated">{new Date(mergeRequest.providerUpdatedAt).toLocaleString()}</MetadataRow><MetadataRow label="Arnold synchronized">{new Date(mergeRequest.lastSyncedAt).toLocaleString()}</MetadataRow></dl></section> : null}

      {runId ? <section className="mt-5"><h2 className="mb-3 font-display text-xl font-bold text-fg">Analysis run</h2>{!analysis && !analysisError ? <LoadingState label="Loading analysis" /> : null}{analysisError ? <ErrorState title="Could not load analysis" error={analysisError} /> : null}{analysis ? <div className="border border-border bg-surface p-4 font-mono text-xs text-muted">{analysis.runStatus} · {analysis.completedPersonas}/{analysis.personaIds.length} personas · {analysis.runId}</div> : null}</section> : null}

      <section className="mt-7"><h2 className="mb-3 font-display text-xl font-bold text-fg">Synchronized discussions</h2>{!discussions && !discussionError ? <LoadingState label="Loading discussions" /> : null}{discussionError ? <ErrorState title="Could not load discussions" error={discussionError} onRetry={reloadDiscussions} /> : null}{discussions ? <DiscussionList discussions={discussions} /> : null}<DiscussionComposer className="mt-4" provider="GITLAB" projectId={repositoryId} changeNumber={number} enabled={providerCapabilities.GITLAB.canCreateDiscussions} onPublished={(discussion) => setDiscussions((current) => [...(current || []), discussion])} onRefresh={reloadDiscussions} /></section>
      <UnavailableState className="mt-7" title="Diff and review actions unavailable" description="Arnold does not currently expose source diffs, findings, replies, approvals, merge, close, or resolution mutations." />
    </div>
  );
}

export default function ReviewPage() {
  return <Suspense fallback={<LoadingState label="Loading review" />}><ReviewContent /></Suspense>;
}
