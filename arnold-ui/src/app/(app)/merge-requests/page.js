'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { GitPullRequest, RefreshCw } from 'lucide-react';
import { Overline } from '@/components/ui/Overline';
import { Tabs } from '@/components/ui/Tabs';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { useAppData } from '@/components/layout/AppLayout';
import { fetchOpenMergeRequests, retrySync } from '@/lib/api';

const filters = [
  { key: 'all', label: 'All' },
  { key: 'author', label: 'Authored' },
  { key: 'assignee', label: 'Assigned' },
  { key: 'reviewer', label: 'Reviewing' },
];

function reviewHref(mergeRequest) {
  return `/review?provider=GITLAB&projectId=${encodeURIComponent(mergeRequest.repositoryId)}&iid=${mergeRequest.changeNumber}`;
}

export default function OpenMergeRequestsPage() {
  const appData = useAppData();
  const [role, setRole] = useState('all');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(null);
  const [status, setStatus] = useState('loading');
  const [error, setError] = useState(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setStatus('loading');
    setError(null);
    fetchOpenMergeRequests({ role, limit: 50, signal: controller.signal })
      .then((response) => {
        if (!controller.signal.aborted) {
          setPage(response);
          setStatus('ready');
        }
      })
      .catch((nextError) => {
        if (!controller.signal.aborted) {
          setError(nextError);
          setStatus('error');
        }
      });
    return () => controller.abort();
  }, [revision, role]);

  async function loadMore() {
    if (!page?.nextCursor) return;
    setLoadingMore(true);
    try {
      const next = await fetchOpenMergeRequests({ role, limit: 50, cursor: page.nextCursor });
      setPage((current) => ({
        ...next,
        items: [...current.items, ...next.items],
      }));
    } catch (nextError) {
      setError(nextError);
    } finally {
      setLoadingMore(false);
    }
  }

  async function syncNow() {
    setSyncing(true);
    try {
      await retrySync();
      const next = await fetchOpenMergeRequests({ role, limit: 50 });
      setPage(next);
      await appData.refreshBootstrap();
    } catch (nextError) {
      setError(nextError);
    } finally {
      setSyncing(false);
    }
  }

  const normalizedQuery = query.trim().toLowerCase();
  const visibleItems = (page?.items || []).filter(({ mergeRequest }) => !normalizedQuery
    || mergeRequest.title.toLowerCase().includes(normalizedQuery)
    || mergeRequest.repositoryPath.toLowerCase().includes(normalizedQuery)
    || String(mergeRequest.changeNumber).includes(normalizedQuery));
  const sync = page?.sync || appData.sync;

  return (
    <div className="mx-auto max-w-[1280px]">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <Overline>GitLab workspace</Overline>
          <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">
            Open merge requests
          </h1>
          <p className="mt-2 max-w-[72ch] text-[15px] text-muted">
            Authored, assigned, and review work from the durable Arnold sync.
          </p>
        </div>
        <Button variant="secondary" disabled={syncing || ['PENDING', 'RUNNING'].includes(sync?.status)} onClick={syncNow}>
          <RefreshCw className={`h-4 w-4 ${syncing ? 'animate-spin' : ''}`} aria-hidden="true" />
          {syncing ? 'Sync queued…' : 'Sync now'}
        </Button>
      </div>

      {sync ? (
        <div className="mb-5 flex flex-wrap items-center gap-3 border border-border bg-surface px-4 py-3">
          <span className={`h-2 w-2 rounded-full ${
            sync.status === 'FAILED' ? 'bg-bad' : ['PENDING', 'RUNNING'].includes(sync.status) ? 'animate-pulse bg-accent' : 'bg-ok'
          }`} />
          <span className="font-mono text-[11px] uppercase tracking-[0.05em] text-fg">{sync.status.replaceAll('_', ' ')}</span>
          <span className="text-[12px] text-muted">
            {sync.lastSucceededAt ? `Last synchronized ${new Date(sync.lastSucceededAt).toLocaleString()}` : 'First sync pending'}
          </span>
          {sync.failure ? <span className="text-[12px] text-bad">{sync.failure}</span> : null}
        </div>
      ) : null}

      <div className="mb-5 flex flex-col gap-3 border-y border-border py-4 sm:flex-row sm:items-center sm:justify-between">
        <Tabs items={filters} activeKey={role} onTabChange={setRole} variant="segment" />
        <Input
          className="sm:max-w-[300px]"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Filter by title, repo, or MR"
          aria-label="Filter open merge requests"
        />
      </div>

      {status === 'loading' ? <LoadingState label="Loading open merge requests" /> : null}
      {status === 'error' ? (
        <ErrorState title="Could not load open merge requests" error={error} onRetry={() => setRevision((value) => value + 1)} />
      ) : null}
      {status === 'ready' && visibleItems.length === 0 ? (
        <EmptyState
          icon={<GitPullRequest />}
          title={query ? 'No matching merge requests' : 'No open merge requests'}
          description={query ? 'Clear the filter or search another repository.' : 'New GitLab work appears here after synchronization.'}
        />
      ) : null}

      <div className="grid gap-2.5">
        {visibleItems.map((item) => {
          const mergeRequest = item.mergeRequest;
          return (
            <Link
              key={mergeRequest.id}
              href={reviewHref(mergeRequest)}
              className={`group grid gap-3 border border-border bg-surface px-4 py-3 transition-colors hover:border-accent md:grid-cols-[90px_minmax(0,1fr)_auto] md:items-center ${
                item.blocked ? 'border-l-[3px] border-l-accent' : ''
              }`}
            >
              <div>
                <span className="inline-block bg-fg px-2 py-1 font-mono text-[11px] font-semibold text-surface">
                  !{mergeRequest.changeNumber}
                </span>
              </div>
              <div className="min-w-0">
                <div className="truncate text-[14px] font-semibold text-fg group-hover:text-accent-ink">{mergeRequest.title}</div>
                <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1 font-mono text-[10.5px] text-muted">
                  <span>{mergeRequest.repositoryPath}</span>
                  <span>{mergeRequest.sourceBranch} → {mergeRequest.targetBranch}</span>
                  <span>Updated {new Date(mergeRequest.providerUpdatedAt).toLocaleDateString()}</span>
                </div>
              </div>
              <div className="flex flex-wrap items-center gap-1.5 md:justify-end">
                {item.roles.map((itemRole) => <Badge key={itemRole}>{itemRole}</Badge>)}
                <Badge variant={item.blocked ? 'warning' : 'success'}>
                  {item.blocked ? 'Blocked' : item.reviewState.replaceAll('_', ' ')}
                </Badge>
              </div>
            </Link>
          );
        })}
      </div>
      {page?.nextCursor ? (
        <div className="mt-5 flex justify-center">
          <Button variant="secondary" disabled={loadingMore} onClick={loadMore}>
            {loadingMore ? 'Loading…' : 'Load more'}
          </Button>
        </div>
      ) : null}
    </div>
  );
}
