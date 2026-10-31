'use client';

/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React, { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Play, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { fetchAllMergeRequests, fetchRepositorySyncStatus } from '@/lib/api';
import { analyzeUrlHref, availableProviders, providerName, repositorySyncState, syncStatusLine } from '@/lib/repository-sync.mjs';
import { MergeRequestIdentity } from './MergeRequestIdentity';

const STATE_OPTIONS = [
  { value: 'open', label: 'Open' },
  { value: 'merged', label: 'Merged' },
  { value: 'closed', label: 'Closed' },
  { value: 'all', label: 'All' },
];

const UNCONFIGURED = 'No repositories are being synced yet. Install the Arnold GitHub App on repositories, or configure a GitLab service token (see docs/setup).';

export function AllOpenMergeRequests({ now, className = '', ...props }) {
  const router = useRouter();
  const [syncStatus, setSyncStatus] = useState(null);
  const [syncError, setSyncError] = useState(null);
  const [syncRefresh, setSyncRefresh] = useState(0);
  const [provider, setProvider] = useState('all');
  const [state, setState] = useState('open');
  const [items, setItems] = useState([]);
  const [nextCursor, setNextCursor] = useState(null);
  const [status, setStatus] = useState('loading');
  const [error, setError] = useState(null);
  const [refresh, setRefresh] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState(null);

  const syncState = syncError ? 'error' : repositorySyncState(syncStatus);

  useEffect(() => {
    const controller = new AbortController();
    fetchRepositorySyncStatus({ signal: controller.signal }).then((next) => {
      if (controller.signal.aborted) return;
      setSyncStatus(next);
      setSyncError(null);
    }).catch((nextError) => !controller.signal.aborted && setSyncError(nextError));
    return () => controller.abort();
  }, [syncRefresh]);

  useEffect(() => {
    if (syncState !== 'running') return undefined;
    const timer = window.setTimeout(() => {
      setSyncRefresh((current) => current + 1);
      setRefresh((current) => current + 1);
    }, 10_000);
    return () => window.clearTimeout(timer);
  }, [syncState, syncStatus]);

  useEffect(() => {
    const controller = new AbortController();
    setStatus((current) => (current === 'ready' ? 'refreshing' : 'loading'));
    setError(null);
    setMoreError(null);
    fetchAllMergeRequests({ provider, state, signal: controller.signal }).then((page) => {
      if (controller.signal.aborted) return;
      setItems(page.items);
      setNextCursor(page.nextCursor || null);
      setStatus('ready');
    }).catch((nextError) => {
      if (controller.signal.aborted) return;
      setError(nextError);
      setStatus('error');
    });
    return () => controller.abort();
  }, [provider, state, refresh]);

  async function loadMore() {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    setMoreError(null);
    try {
      const page = await fetchAllMergeRequests({ provider, state, cursor: nextCursor });
      setItems((current) => {
        const seen = new Set(current.map((item) => item.mergeRequest.id));
        return [...current, ...page.items.filter((item) => !seen.has(item.mergeRequest.id))];
      });
      setNextCursor(page.nextCursor || null);
    } catch (nextError) {
      setMoreError(nextError);
    } finally {
      setLoadingMore(false);
    }
  }

  function reloadAll() {
    setSyncRefresh((current) => current + 1);
    setRefresh((current) => current + 1);
  }

  const providers = availableProviders(syncStatus);
  const syncedProviders = (syncStatus?.providers || []).filter((entry) => entry.available);
  const stateLabel = STATE_OPTIONS.find((option) => option.value === state)?.label.toLowerCase();

  let body;
  if (status === 'loading') body = <LoadingState label="Loading merge requests and pull requests" />;
  else if (status === 'error') body = <ErrorState title="Could not load merge requests and pull requests" error={error} onRetry={reloadAll} />;
  else if (items.length === 0) {
    body = syncState === 'unconfigured'
      ? <EmptyState className="border-0" title="Nothing synced yet" description={UNCONFIGURED} />
      : syncState === 'running'
        ? <LoadingState label="Repository sync is running. Merge requests and pull requests appear here as they are imported…" />
        : <EmptyState className="border-0" title="No merge requests or pull requests" description={`No ${state === 'all' ? '' : `${stateLabel} `}merge requests or pull requests match these filters.`} />;
  } else {
    body = (
      <div>
        <div className="hidden border-b border-border bg-surface-2 px-4 py-2 font-mono text-[9px] uppercase tracking-widest text-faint xl:grid xl:grid-cols-[minmax(0,1fr)_160px_65px] xl:gap-3"><span>Merge request / pull request</span><span>Review / blockers</span><span>Updated</span></div>
        {items.map((item) => {
          const open = ['open', 'opened'].includes(item.mergeRequest.state?.toLowerCase());
          return (
            <article key={item.mergeRequest.id} className={`relative border-b border-border bg-surface transition-colors hover:bg-[var(--ink-wash)] ${item.blocked && open ? 'border-l-[3px] border-l-accent' : ''}`}>
              <div className="flex items-center gap-3 px-4 py-3">
                <MergeRequestIdentity now={now} item={item} showProvider={providers.length > 1} />
                {open ? (
                  <Button className="relative z-10 flex-none" variant="secondary" size="xs" onClick={() => router.push(analyzeUrlHref(item.mergeRequest))}>
                    <Play className="h-3.5 w-3.5" aria-hidden="true" />
                    Run analysis
                  </Button>
                ) : null}
              </div>
            </article>
          );
        })}
        {moreError ? <ErrorState className="m-4" title="Could not load more" error={moreError} onRetry={loadMore} /> : null}
        {nextCursor ? (
          <div className="flex justify-center p-4">
            <Button variant="secondary" onClick={loadMore} disabled={loadingMore}>{loadingMore ? 'Loading…' : 'Load more'}</Button>
          </div>
        ) : null}
      </div>
    );
  }

  return (
    <section aria-labelledby="all-open-title" className={`min-w-0 border border-border bg-surface ${className}`} {...props}>
      <header className="flex flex-wrap items-start justify-between gap-3 border-b border-border px-4 py-4">
        <div className="min-w-0">
          <h2 id="all-open-title" className="font-display text-lg font-semibold text-fg">Across synced repositories</h2>
          <div role="status" className="mt-1 flex flex-col gap-0.5 font-mono text-[11px] text-muted">
            {syncError ? <span className="text-accent-ink">Repository sync status unavailable.</span>
              : !syncStatus ? <span>Checking repository sync…</span>
                : syncedProviders.length === 0 ? <span>No repository sync configured.</span>
                  : syncedProviders.map((entry) => <span key={entry.provider} className={entry.failure || String(entry.status).toUpperCase() === 'FAILED' ? 'text-accent-ink' : undefined}>{syncStatusLine(entry, now)}</span>)}
          </div>
        </div>
        <Button variant="ghost" size="xs" onClick={reloadAll} aria-label="Refresh merge requests and pull requests" disabled={status === 'loading' || status === 'refreshing'}>
          <RefreshCw className={`h-4 w-4 ${status === 'refreshing' ? 'animate-spin' : ''}`} aria-hidden="true" />
        </Button>
      </header>
      <div className="flex flex-wrap gap-2 border-b border-border p-3">
        {providers.length > 1 ? (
          <select aria-label="Provider" value={provider} onChange={(event) => setProvider(event.target.value)} className="h-9 border border-border bg-surface px-3 text-[12px] text-fg focus-visible:outline-accent">
            <option value="all">All providers</option>
            {providers.map((value) => <option key={value} value={value}>{providerName(value)}</option>)}
          </select>
        ) : null}
        <select aria-label="State" value={state} onChange={(event) => setState(event.target.value)} className="h-9 border border-border bg-surface px-3 text-[12px] text-fg focus-visible:outline-accent">
          {STATE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
        <span className="self-center font-mono text-[10px] text-muted">{status === 'ready' || status === 'refreshing' ? `${items.length}${nextCursor ? '+' : ''} shown` : '—'}</span>
      </div>
      {syncState === 'running' && items.length > 0 ? <p className="border-b border-border px-4 py-2 text-xs text-muted">Repository sync is running — new items will appear shortly.</p> : null}
      {syncState === 'unconfigured' && items.length > 0 ? <p className="border-b border-border px-4 py-2 text-xs text-muted">{UNCONFIGURED}</p> : null}
      {body}
    </section>
  );
}
