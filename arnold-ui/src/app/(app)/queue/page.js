'use client';

import React, { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { GripVertical, Trash2 } from 'lucide-react';
import { Overline } from '@/components/ui/Overline';
import { Tabs } from '@/components/ui/Tabs';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { useAppData } from '@/components/layout/AppLayout';
import { addMergeQueueItem, removeMergeQueueItem, reorderMergeQueue, resolveMergeRequest } from '@/lib/api';
import { parseChangeRequestId } from '@/lib/contracts';
import { hydrateMergeQueue } from '@/lib/merge-queue';

const filters = [
  { key: 'all', label: 'All' },
  { key: 'assigned', label: 'Assigned' },
  { key: 'watching', label: 'Watching' },
  { key: 'blocked', label: 'Blocked' },
];

function matchesFilter(item, filter) {
  if (filter === 'assigned') return item.role === 'author' || item.role === 'reviewer';
  if (filter === 'watching') return item.role === 'watcher';
  if (filter === 'blocked') return item.blocked;
  return true;
}

export default function QueuePage() {
  const router = useRouter();
  const appData = useAppData();
  const [queue, setLocalQueue] = useState(appData.queue);
  const [detailsById, setDetailsById] = useState({});
  const [hydrationErrors, setHydrationErrors] = useState({});
  const [status, setStatus] = useState(appData.queue ? 'ready' : appData.queueError ? 'error' : 'loading');
  const [error, setError] = useState(appData.queueError);
  const [notice, setNotice] = useState('');
  const [filter, setFilter] = useState('all');
  const [url, setUrl] = useState('');
  const [role, setRole] = useState('reviewer');
  const [mutation, setMutation] = useState(null);
  const [dragId, setDragId] = useState(null);

  function updateQueue(nextQueue) {
    setLocalQueue(nextQueue);
    appData.setQueue(nextQueue);
    setStatus('ready');
  }

  async function reload() {
    setStatus('loading');
    setError(null);
    try {
      updateQueue(await appData.refreshQueue());
    } catch (nextError) {
      setError(nextError);
      setStatus('error');
    }
  }

  useEffect(() => {
    if (appData.queue && !queue) {
      setLocalQueue(appData.queue);
      setStatus('ready');
    }
  }, [appData.queue, queue]);

  useEffect(() => {
    if (!queue) return undefined;
    const controller = new AbortController();
    hydrateMergeQueue(queue, { signal: controller.signal }).then(({ detailsById: details, errorsById }) => {
      if (!controller.signal.aborted) {
        setDetailsById(details);
        setHydrationErrors(errorsById);
      }
    });
    return () => controller.abort();
  }, [queue]);

  async function addItem(event) {
    event.preventDefault();
    const trimmedUrl = url.trim();
    if (!trimmedUrl) return;
    setMutation({ type: 'add' });
    setError(null);
    try {
      const mergeRequest = await resolveMergeRequest(trimmedUrl);
      updateQueue(await addMergeQueueItem({ mergeRequestId: mergeRequest.id, role }));
      setDetailsById((current) => ({ ...current, [mergeRequest.id]: mergeRequest }));
      setUrl('');
    } catch (nextError) {
      setError(nextError);
    } finally {
      setMutation(null);
    }
  }

  async function removeItem(itemId) {
    setMutation({ type: 'remove', itemId });
    setError(null);
    try {
      updateQueue(await removeMergeQueueItem(itemId));
    } catch (nextError) {
      setError(nextError);
    } finally {
      setMutation(null);
    }
  }

  async function moveItem(fromId, toId) {
    if (!queue || mutation || fromId === toId) return;
    const snapshot = queue;
    const items = [...queue.items];
    const fromIndex = items.findIndex((item) => item.id === fromId);
    const toIndex = items.findIndex((item) => item.id === toId);
    const [moved] = items.splice(fromIndex, 1);
    items.splice(toIndex, 0, moved);
    updateQueue({ ...queue, items });
    setMutation({ type: 'reorder' });
    setError(null);
    try {
      updateQueue(await reorderMergeQueue({ orderedItemIds: items.map((item) => item.id), version: snapshot.version }));
    } catch (nextError) {
      if (nextError.status === 409 || nextError.errorCode === 3001) {
        try {
          updateQueue(await appData.refreshQueue());
          setNotice('Queue changed in another session. Latest order loaded.');
        } catch (reloadError) {
          updateQueue(snapshot);
          setError(reloadError);
        }
      } else {
        updateQueue(snapshot);
        setError(nextError);
      }
    } finally {
      setMutation(null);
      setDragId(null);
    }
  }

  const visibleItems = queue?.items.filter((item) => matchesFilter(item, filter)) || [];

  return (
    <div className="mx-auto max-w-[1680px]">
      <div className="mb-6">
        <Overline>Personal merge queue</Overline>
        <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">Queue &amp; prioritise</h1>
        <p className="mt-2 max-w-[72ch] text-[15px] text-muted">Your persisted review queue. Filters are applied locally; drag rows to save a new order.</p>
      </div>

      <form onSubmit={addItem} className="mb-5 grid gap-3 border border-border bg-surface p-4 md:grid-cols-[minmax(0,1fr)_150px_auto]">
        <Input aria-label="Synchronized GitLab merge request URL" placeholder="GitLab merge request URL" value={url} onChange={(event) => setUrl(event.target.value)} disabled={mutation?.type === 'add'} />
        <select className="h-9 border border-border-2 bg-surface-2 px-3 text-sm text-fg" value={role} onChange={(event) => setRole(event.target.value)} disabled={mutation?.type === 'add'} aria-label="Queue role">
          <option value="author">Author</option><option value="reviewer">Reviewer</option><option value="watcher">Watcher</option>
        </select>
        <Button type="submit" disabled={!url.trim() || mutation?.type === 'add'} aria-busy={mutation?.type === 'add'}>{mutation?.type === 'add' ? 'Adding…' : 'Add synchronized MR'}</Button>
      </form>

      <Tabs className="mb-4" variant="segment" items={filters} activeKey={filter} onTabChange={setFilter} />
      {notice ? <p role="status" className="mb-4 border border-tertiary bg-surface px-4 py-3 text-sm text-muted">{notice}</p> : null}
      {error && status !== 'error' ? <ErrorState className="mb-4" title="Queue operation failed" error={error} /> : null}
      {status === 'loading' ? <LoadingState label="Loading queue" /> : null}
      {status === 'error' ? <ErrorState title="Could not load queue" error={error} onRetry={reload} /> : null}
      {status === 'ready' && visibleItems.length === 0 ? <EmptyState title={queue?.items.length ? 'No matching queue items' : 'Queue empty'} description={queue?.items.length ? 'Choose another local filter.' : 'Add a synchronized merge request by URL.'} /> : null}

      <div className="flex flex-col gap-2.5">
        {visibleItems.map((item) => {
          const detail = detailsById[item.changeRequestId];
          const identity = parseChangeRequestId(item.changeRequestId);
          return (
            <div
              key={item.id}
              draggable={!mutation}
              onDragStart={() => setDragId(item.id)}
              onDragOver={(event) => event.preventDefault()}
              onDrop={() => moveItem(dragId, item.id)}
              className="flex items-center gap-3 border border-border bg-surface px-4 py-3 hover:border-accent"
            >
              <GripVertical className="h-4 w-4 flex-none text-faint" aria-hidden="true" />
              <button className="min-w-0 flex-1 text-left" onClick={() => router.push(`/review?provider=GITLAB&projectId=${encodeURIComponent(identity.repositoryId)}&iid=${identity.number}`)}>
                <div className="flex flex-wrap items-center gap-2"><span className="font-mono text-xs font-semibold text-accent-ink">!{identity.number}</span><span className="text-sm font-semibold text-fg">{detail?.title || 'MR details unavailable'}</span></div>
                <div className="mt-1 font-mono text-[11px] text-muted">{detail ? `${detail.repositoryPath} · ${detail.sourceBranch} → ${detail.targetBranch}` : item.changeRequestId}{hydrationErrors[item.changeRequestId] ? ' · hydration failed' : ''}</div>
              </button>
              <span className="font-mono text-[10px] uppercase text-muted">{item.role}{item.blocked ? ' · blocked' : ''}</span>
              <Button variant="ghost" size="xs" aria-label={`Remove merge request !${identity.number}`} disabled={mutation?.type === 'remove' && mutation.itemId === item.id} onClick={() => removeItem(item.id)}><Trash2 className="h-4 w-4" /></Button>
            </div>
          );
        })}
      </div>
    </div>
  );
}
