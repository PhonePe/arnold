'use client';
import React, { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter, useParams, useSearchParams } from 'next/navigation';
import { Button } from '@/components/ui/Button';
import { Avatar } from '@/components/ui/Avatar';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { Markdown } from '@/components/ui/Markdown';
import { StaleDataNotice } from '@/components/ui/StaleDataNotice';
import { UnavailableState } from '@/components/ui/UnavailableState';
import { DiscussionList } from '@/components/composite/DiscussionList';
import { DiscussionComposer } from '@/components/composite/DiscussionComposer';
import { DiffViewer } from '@/components/composite/DiffViewer';
import { ReviewPublishBar } from '@/components/composite/ReviewPublishBar';
import { fetchAnalysis, fetchDiscussions, fetchDraftDiscussions, fetchMergeRequest, fetchMergeRequestDiff, fetchMergeRequestDiffVersion, fetchMergeRequestDiffVersions, fetchMergeRequestReview } from '@/lib/api';
import { providerCapabilities } from '@/lib/capabilities';
import { replaceDraftDiscussions } from '@/lib/discussion-state.mjs';
import { groupDiscussions } from '@/lib/discussions';
import { isCurrentPosition } from '@/lib/diff-lines.mjs';
import { analysisEntry } from '@/lib/analysis-navigation.mjs';
import { useAppData } from '@/components/layout/AppLayout';

const INSIGHT_TABS = ['verdict', 'build', 'details', 'activity'];

function initials(name) {
  return name?.split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase() || '?';
}

function People({ names }) {
  if (!names.length) return <span className="text-muted">None</span>;
  return <div className="space-y-2">{names.map((name) => <div key={name} className="flex items-center gap-2"><Avatar size="sm" initials={initials(name)} alt={name} /><span>{name}</span></div>)}</div>;
}

const reviewerLabels = { approved: 'Approved', reviewed: 'Reviewed', review_started: 'Review in progress', requested_changes: 'Requested changes', awaiting_review: 'Awaiting review' };

function Reviewers({ reviewers, discussions }) {
  if (!reviewers?.length) return <span className="text-muted">None</span>;
  const drafting = new Set(discussions.filter((note) => note.state === 'draft').map((note) => note.authorUsername));
  const requestedChanges = new Set(discussions.filter((note) => note.state === 'system' && /request(?:ed)? changes/i.test(note.body)).map((note) => note.authorUsername));
  return <div className="space-y-3">{reviewers.map((reviewer) => {
    const status = reviewer.status === 'approved' ? 'approved' : requestedChanges.has(reviewer.username) ? 'requested_changes' : drafting.has(reviewer.username) ? 'review_started' : reviewer.status;
    return <div key={reviewer.id} className="flex items-center gap-2"><Avatar size="sm" src={reviewer.avatarUrl} initials={initials(reviewer.name)} alt={reviewer.name} /><div className="min-w-0"><p className="truncate">{reviewer.name}</p><p className={`font-mono text-xs ${status === 'approved' ? 'text-ok' : status === 'requested_changes' ? 'text-bad' : 'text-muted'}`}>{reviewerLabels[status] || status}</p></div></div>;
  })}</div>;
}

function MergeCheck({ label, value, ok }) {
  return <div className="flex items-center justify-between gap-3 border-t border-border py-2 first:border-t-0"><span className="text-sm text-muted">{label}</span><span className={`font-mono text-sm ${ok == null ? 'text-muted' : ok ? 'text-ok' : 'text-bad'}`}>{value}</span></div>;
}

function InsightCard({ title, children }) {
  return <section className="rc"><div className="rch"><h4>{title}</h4></div><div className="rcb">{children}</div></section>;
}

function ReviewInsights({ mergeRequest, reviewStatus, reviewStatusError, analysis, analysisError, discussions, discussionError, filteredActivity, activityFilter, activityDescending, overviewThreadCount, commentsEnabled, projectId, changeNumber, headSha, onActivityFilterChange, onActivitySortChange, onRefresh }) {
  const [activeTab, setActiveTab] = useState('verdict');
  const checks = reviewStatus ? [
    !reviewStatus.hasConflicts,
    reviewStatus.discussionsResolved,
    reviewStatus.approvalsLeft === 0,
    reviewStatus.pipelineStatus !== 'failed',
  ] : [];
  const passedChecks = checks.filter(Boolean).length;

  return <div className="flex min-h-full flex-col">
    <div className="drawer-tabs !gap-0 !px-2 [&_button]:min-w-0 [&_button]:flex-1 [&_button]:!px-1 [&_button]:!text-xs" role="tablist" aria-label="Review insights">
      {INSIGHT_TABS.map((tab) => <button key={tab} className={activeTab === tab ? 'on' : ''} role="tab" aria-selected={activeTab === tab} onClick={() => setActiveTab(tab)}>{tab}</button>)}
    </div>
    <div className="drawer-body">
      {activeTab === 'verdict' ? <div className="dpanel space-y-3">
        <InsightCard title="Overall verdict">
          <div className="score-row">
            <div className="score"><div className="lab2">Merge state</div><div className={`big ${mergeRequest?.hasConflicts ? 'bad' : ''}`}>{mergeRequest?.draft ? 'Draft' : mergeRequest?.state || '—'}</div><div className="sub">{mergeRequest?.mergeStatus || 'Checking GitLab'}</div></div>
            <div className="score"><div className="lab2">Health</div><div className={`big ${passedChecks < checks.length ? 'bad' : ''}`}>{checks.length ? `${passedChecks}/${checks.length}` : '—'}</div><div className="sub">merge checks passing</div></div>
          </div>
        </InsightCard>
        <InsightCard title="Merge checks">
          {!reviewStatus && !reviewStatusError ? <LoadingState label="Loading merge checks" /> : null}
          {reviewStatusError ? <ErrorState title="Could not load merge checks" error={reviewStatusError} onRetry={onRefresh} /> : null}
          {reviewStatus ? <><MergeCheck label="Mergeability" value={reviewStatus.detailedMergeStatus || mergeRequest?.mergeStatus} ok={['checking', 'preparing', 'approvals_syncing'].includes(reviewStatus.detailedMergeStatus) ? null : reviewStatus.detailedMergeStatus === 'mergeable'} /><MergeCheck label="Approvals" value={reviewStatus.approvalsRequired ? `${reviewStatus.approvalsRequired - reviewStatus.approvalsLeft}/${reviewStatus.approvalsRequired}` : reviewStatus.approved ? 'Approved' : 'Not required'} ok={reviewStatus.approvalsLeft === 0} /><MergeCheck label="Discussions" value={reviewStatus.discussionsResolved ? 'Resolved' : 'Unresolved'} ok={reviewStatus.discussionsResolved} /><MergeCheck label="Conflicts" value={reviewStatus.hasConflicts ? 'Has conflicts' : 'None'} ok={!reviewStatus.hasConflicts} /></> : null}
        </InsightCard>
      </div> : null}

      {activeTab === 'build' ? <div className="dpanel space-y-3">
        <InsightCard title="Pipeline">
          {!reviewStatus && !reviewStatusError ? <LoadingState label="Loading pipeline" /> : null}
          {reviewStatus ? <><div className="flex items-center justify-between"><span className="font-mono text-sm text-muted">Latest pipeline</span><Badge variant={reviewStatus.pipelineStatus === 'success' ? 'success' : reviewStatus.pipelineStatus === 'failed' ? 'critical' : 'default'}>{reviewStatus.pipelineStatus || 'No pipeline'}</Badge></div>{reviewStatus.pipelineUrl ? <a className="mt-3 inline-block font-mono text-sm text-accent-ink hover:underline" href={reviewStatus.pipelineUrl} target="_blank" rel="noreferrer">Open pipeline ↗</a> : null}</> : null}
        </InsightCard>
        <InsightCard title="Analysis">
          {!analysis && !analysisError ? <p className="text-sm text-muted">Open this review from an analysis run to see its state.</p> : null}
          {analysisError ? <ErrorState title="Could not load analysis" error={analysisError} /> : null}
          {analysis ? <div className="space-y-2 font-mono text-sm text-muted"><p className="text-fg">{analysis.runStatus}</p><p>{analysis.completedPersonas}/{analysis.personaIds.length} personas complete</p><p className="break-all text-faint">{analysis.runId}</p></div> : null}
        </InsightCard>
      </div> : null}

      {activeTab === 'details' ? <div className="dpanel space-y-3">
        <InsightCard title="Merge request">
          <div className="space-y-3 text-sm"><div><p className="font-mono text-xs uppercase text-faint">Description</p><div className="mt-1">{mergeRequest?.description ? <Markdown>{mergeRequest.description}</Markdown> : <p className="text-muted">No description provided.</p>}</div></div><div><p className="font-mono text-xs uppercase text-faint">Branches</p><p className="mt-1 break-all font-mono text-sm">{mergeRequest?.sourceBranch} → {mergeRequest?.targetBranch}</p></div>{mergeRequest?.labels?.length ? <div><p className="font-mono text-xs uppercase text-faint">Labels</p><div className="mt-2 flex flex-wrap gap-1">{mergeRequest.labels.map((label) => <Badge key={label}>{label}</Badge>)}</div></div> : null}</div>
        </InsightCard>
        <InsightCard title="People">
          <div className="space-y-4"><div><p className="mb-2 font-mono text-xs uppercase text-faint">Author</p><People names={mergeRequest?.authorName ? [mergeRequest.authorName] : []} /></div><div><p className="mb-2 font-mono text-xs uppercase text-faint">Assignees</p><People names={mergeRequest?.assigneeNames || []} /></div><div><p className="mb-2 font-mono text-xs uppercase text-faint">Reviewers</p>{reviewStatus ? <Reviewers reviewers={reviewStatus.reviewers} discussions={discussions || []} /> : <People names={mergeRequest?.reviewerNames || []} />}</div></div>
        </InsightCard>
        <InsightCard title="Synchronization"><p className="font-mono text-sm text-muted">{mergeRequest?.lastSyncedAt ? new Date(mergeRequest.lastSyncedAt).toLocaleString() : 'Waiting for sync'}</p>{mergeRequest?.lastSyncedAt ? <StaleDataNotice className="mt-2" syncedAt={mergeRequest.lastSyncedAt} /> : null}</InsightCard>
      </div> : null}

      {activeTab === 'activity' ? <div className="dpanel space-y-3">
        <InsightCard title={`Activity · ${overviewThreadCount}`}>
          <div className="mb-3 flex gap-2"><select className="h-7 min-w-0 flex-1 border border-border-2 bg-surface-2 px-2 font-mono text-sm text-fg" aria-label="Filter activity" value={activityFilter} onChange={(event) => onActivityFilterChange(event.target.value)}><option value="all">All activity</option><option value="comments">Comments only</option><option value="history">History only</option></select><Button size="xs" variant="secondary" onClick={onActivitySortChange}>{activityDescending ? 'Newest' : 'Oldest'}</Button></div>
          {!discussions && !discussionError ? <LoadingState label="Loading discussions" /> : null}
          {discussionError ? <ErrorState title="Could not load discussions" error={discussionError} onRetry={onRefresh} /> : null}
          {discussions ? <DiscussionList discussions={filteredActivity} provider="GITLAB" projectId={projectId} changeNumber={changeNumber} headSha={headSha} enabled={commentsEnabled} descending={activityDescending} showPosition onChanged={onRefresh} /> : null}
          <DiscussionComposer className="mt-3" provider="GITLAB" projectId={projectId} changeNumber={changeNumber} enabled={commentsEnabled} onChanged={onRefresh} onRefresh={onRefresh} />
        </InsightCard>
      </div> : null}
    </div>
  </div>;
}

export function ReviewWorkspace() {
  const router = useRouter();
  const pathParams = useParams();
  const params = useSearchParams();
  const appData = useAppData();
  const provider = (pathParams.provider || params.get('provider') || 'GITLAB').toUpperCase();
  const repositoryId = pathParams.projectId || params.get('repositoryId') || params.get('projectId');
  const number = pathParams.iid || params.get('number') || params.get('iid');
  const runId = pathParams.runId || params.get('runId');
  const validIdentity = repositoryId && /^\d+$/.test(number || '') && Number(number) > 0;
  const requestKey = `${provider}:${repositoryId}:${number}`;
  const [mergeRequest, setMergeRequest] = useState(null);
  const [mrError, setMrError] = useState(null);
  const [diffState, setDiffState] = useState({ key: null, value: null, error: null });
  const [discussions, setDiscussions] = useState(null);
  const [discussionError, setDiscussionError] = useState(null);
  const [analysis, setAnalysis] = useState(null);
  const [analysisError, setAnalysisError] = useState(null);
  const [diffVersions, setDiffVersions] = useState([]);
  const [selectedVersion, setSelectedVersion] = useState('latest');
  const [reviewStatus, setReviewStatus] = useState(null);
  const [reviewStatusError, setReviewStatusError] = useState(null);
  const [activityFilter, setActivityFilter] = useState('all');
  const [activityDescending, setActivityDescending] = useState(false);
  const [focusThreadId, setFocusThreadId] = useState(null);
  const [compactRequestKey, setCompactRequestKey] = useState(null);
  const headerCompact = compactRequestKey === requestKey;
  const updateHeaderCompact = useCallback((compact) => setCompactRequestKey(compact ? requestKey : null), [requestKey]);

  const reloadDiscussions = useCallback(async () => {
    if (!validIdentity || provider !== 'GITLAB') return;
    setDiscussionError(null);
    setReviewStatusError(null);
    const [nextDiscussions, nextReviewStatus] = await Promise.allSettled([
      fetchDiscussions(repositoryId, number), fetchMergeRequestReview(repositoryId, number),
    ]);
    if (nextDiscussions.status === 'fulfilled') setDiscussions(nextDiscussions.value); else setDiscussionError(nextDiscussions.reason);
    if (nextReviewStatus.status === 'fulfilled') setReviewStatus(nextReviewStatus.value); else setReviewStatusError(nextReviewStatus.reason);
  }, [number, provider, repositoryId, validIdentity]);

  const reloadDrafts = useCallback(async () => {
    try {
      const drafts = await fetchDraftDiscussions(repositoryId, number);
      setDiscussions((current) => replaceDraftDiscussions(current, drafts));
    } catch (error) {
      setDiscussionError(error);
    }
  }, [number, repositoryId]);

  const reloadDiff = useCallback(async () => {
    if (!validIdentity || provider !== 'GITLAB') return;
    setDiffState({ key: requestKey, value: null, error: null });
    try {
      setDiffState({ key: requestKey, value: await fetchMergeRequestDiff(repositoryId, number), error: null });
    } catch (error) {
      setDiffState({ key: requestKey, value: null, error });
    }
  }, [number, provider, repositoryId, requestKey, validIdentity]);

  const changeDiffVersion = useCallback(async (versionId) => {
    setSelectedVersion(versionId);
    setDiffState({ key: requestKey, value: null, error: null });
    try {
      const value = versionId === 'latest'
        ? await fetchMergeRequestDiff(repositoryId, number)
        : await fetchMergeRequestDiffVersion(repositoryId, number, versionId);
      setDiffState({ key: requestKey, value, error: null });
    } catch (error) {
      setDiffState({ key: requestKey, value: null, error });
    }
  }, [number, repositoryId, requestKey]);

  useEffect(() => {
    if (!validIdentity || provider !== 'GITLAB') return undefined;
    const controller = new AbortController();
    fetchMergeRequest(repositoryId, number, { provider, signal: controller.signal }).then(setMergeRequest).catch((error) => !controller.signal.aborted && setMrError(error));
    fetchMergeRequestDiff(repositoryId, number, { signal: controller.signal })
      .then((value) => setDiffState({ key: requestKey, value, error: null }))
      .catch((error) => !controller.signal.aborted && setDiffState({ key: requestKey, value: null, error }));
    fetchMergeRequestDiffVersions(repositoryId, number, { signal: controller.signal }).then(setDiffVersions).catch(() => setDiffVersions([]));
    fetchDiscussions(repositoryId, number, { signal: controller.signal }).then(setDiscussions).catch((error) => !controller.signal.aborted && setDiscussionError(error));
    fetchMergeRequestReview(repositoryId, number, { signal: controller.signal }).then(setReviewStatus).catch((error) => !controller.signal.aborted && setReviewStatusError(error));
    if (runId) fetchAnalysis(runId, { signal: controller.signal }).then(setAnalysis).catch((error) => !controller.signal.aborted && setAnalysisError(error));
    return () => controller.abort();
  }, [number, provider, repositoryId, requestKey, runId, validIdentity]);

  const diff = diffState.key === requestKey ? diffState.value : null;
  const diffError = diffState.key === requestKey ? diffState.error : null;
  const commentsEnabled = providerCapabilities.GITLAB.canCreateDiscussions;
  const allThreads = discussions ? groupDiscussions(discussions) : [];
  const overviewDiscussions = allThreads.flatMap((thread) => thread.notes);
  const filteredActivity = overviewDiscussions.filter((note) => activityFilter === 'all' || (activityFilter === 'history') === (note.state === 'system'));
  const outdatedDiscussions = allThreads.filter((thread) => thread.position && !isCurrentPosition(thread.position, mergeRequest?.headSha)).flatMap((thread) => thread.notes).filter((note) => note.state !== 'system');
  const overviewThreadCount = groupDiscussions(overviewDiscussions).length;
  const activeDiffVersion = selectedVersion === 'latest' ? diffVersions[0] : diffVersions.find((version) => String(version.id) === selectedVersion);

  if (!validIdentity) return <EmptyState title="Invalid review route" description="Choose a synchronized merge request from your queue." action={<Link href="/queue"><Button>Open queue</Button></Link>} />;
  if (provider !== 'GITLAB') return <UnavailableState title={`${provider} reviews unavailable`} description="Only the GitLab provider is operational." />;

  const currentHeadSha = activeDiffVersion?.headSha || mergeRequest?.headSha;
  const summary = appData.openMergeRequests.find((item) => item.mergeRequest.id === mergeRequest?.id);
  const terminalState = ['closed', 'merged'].includes(mergeRequest?.state?.toLowerCase())
    ? mergeRequest.state
    : analysis?.reviewState || summary?.reviewState || mergeRequest?.state;
  const entry = mergeRequest ? analysisEntry(terminalState, mergeRequest.id) : null;
  const insights = <ReviewInsights mergeRequest={mergeRequest} reviewStatus={reviewStatus} reviewStatusError={reviewStatusError} analysis={analysis} analysisError={analysisError} discussions={discussions} discussionError={discussionError} filteredActivity={filteredActivity} activityFilter={activityFilter} activityDescending={activityDescending} overviewThreadCount={overviewThreadCount} commentsEnabled={commentsEnabled} projectId={repositoryId} changeNumber={number} headSha={currentHeadSha} onActivityFilterChange={setActivityFilter} onActivitySortChange={() => setActivityDescending((value) => !value)} onRefresh={reloadDiscussions} />;

  return <div className="rvx h-[calc(100dvh-60px)]" data-state={analysis?.runStatus || mergeRequest?.state || 'loading'}>
    <header className="rvx-head" data-compact={headerCompact}>
      <div className="rvx-head-t">
        <p className="eyebrow">Merge request · GitLab</p>
        <div className="rvx-title-row">
          {mergeRequest ? <span className="compact-ref">!{mergeRequest.changeNumber}</span> : null}
          <h1 className="ttl">{mergeRequest?.title || 'Review workspace'}</h1>
        </div>
        <p className="meta">{mergeRequest ? `${mergeRequest.repositoryPath} · !${mergeRequest.changeNumber} · ${mergeRequest.sourceBranch} → ${mergeRequest.targetBranch}` : `Project ${repositoryId} · !${number}`}</p>
      </div>
      <span className="spacer" />
      <span className="statepill">{(analysis?.runStatus || (mergeRequest?.draft ? 'DRAFT' : mergeRequest?.state || 'LOADING')).toUpperCase()}</span>
      {entry ? <button type="button" className="btn btn-pri btn-sm" onClick={() => router.push(entry.href)}>{entry.label}</button> : null}
      {mergeRequest ? <a className="btn btn-sec btn-sm" href={mergeRequest.webUrl} target="_blank" rel="noreferrer">Open in GitLab ↗</a> : null}
    </header>
    {!mergeRequest && !mrError ? <LoadingState className="m-5" label="Loading merge request" /> : null}
    {mrError ? <ErrorState className="m-5" title="Could not load merge request" error={mrError} /> : null}
    {diff === null && !diffError ? <LoadingState className="m-5" label="Loading source changes" /> : null}
    {diffError ? <ErrorState className="m-5" title="Could not load source changes" error={diffError} onRetry={reloadDiff} /> : null}
    {diff !== null && !diffError ? <DiffViewer className="min-h-0 flex-1" patch={diff} discussions={discussions || []} headSha={currentHeadSha} baseSha={activeDiffVersion?.baseSha} startSha={activeDiffVersion?.startSha} webUrl={mergeRequest?.webUrl} versions={diffVersions} selectedVersion={selectedVersion} onVersionChange={changeDiffVersion} provider="GITLAB" projectId={repositoryId} changeNumber={number} enabled={commentsEnabled} onChanged={reloadDiscussions} onScrolledChange={updateHeaderCompact} focusThreadId={focusThreadId} insights={insights} /> : null}
    {outdatedDiscussions.length ? <details className="border-t border-border bg-surface"><summary className="cursor-pointer px-4 py-2 font-mono text-sm text-accent-ink">{outdatedDiscussions.length} outdated comments</summary><div className="max-h-64 overflow-auto border-t border-border p-3"><DiscussionList discussions={outdatedDiscussions} provider="GITLAB" projectId={repositoryId} changeNumber={number} headSha={currentHeadSha} enabled={commentsEnabled} onChanged={reloadDiscussions} /></div></details> : null}
    {discussions ? <ReviewPublishBar provider="GITLAB" projectId={repositoryId} changeNumber={number} discussions={discussions} enabled={commentsEnabled} onChanged={reloadDrafts} onPublished={reloadDiscussions} onNavigate={(discussionId) => setFocusThreadId({ discussionId, at: Date.now() })} /> : null}
  </div>;
}
