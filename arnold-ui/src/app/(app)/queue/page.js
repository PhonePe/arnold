'use client';

import React, { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ChevronRight, GripVertical, Layers3, Plus, Trash2, X } from 'lucide-react';
import { Overline } from '@/components/ui/Overline';
import { Tabs } from '@/components/ui/Tabs';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { useAppData } from '@/components/layout/AppLayout';
import {
  createMergeRequestGroup,
  deleteMergeRequestGroup,
  fetchOpenMergeRequests,
  reorderEnrichedMergeQueue,
} from '@/lib/api';

const filters = [
  { key: 'all', label: 'All' },
  { key: 'author', label: 'Authored' },
  { key: 'reviewer', label: 'Reviewing' },
  { key: 'ready-for-merge', label: 'Ready to merge' },
  { key: 'blocked', label: 'Blocked' },
];

function leafSummaries(item) {
  return item.targetType === 'GROUP' ? item.group.members : [item.mergeRequest];
}

function matchesFilter(item, filter) {
  if (filter === 'blocked') return item.blocked;
  if (filter === 'ready-for-merge') {
    return leafSummaries(item).some((member) => member.reviewState === 'READY_FOR_MERGE');
  }
  if (filter === 'all') return true;
  return leafSummaries(item).some((member) => member.roles.includes(filter));
}

function reviewHref(mergeRequest) {
  return `/review?provider=GITLAB&projectId=${encodeURIComponent(mergeRequest.repositoryId)}&iid=${mergeRequest.changeNumber}`;
}

function MergeRequestIdentity({ item, compact = false, onOpen }) {
  const mergeRequest = item.mergeRequest;
  return (
    <button type="button" onClick={() => onOpen(mergeRequest)} className="min-w-0 flex-1 text-left">
      <div className="flex min-w-0 flex-wrap items-center gap-2">
        <span className="bg-fg px-2 py-1 font-mono text-[11px] font-semibold text-surface">
          !{mergeRequest.changeNumber}
        </span>
        <span className="min-w-0 flex-1 truncate text-[14px] font-semibold text-fg">{mergeRequest.title}</span>
        {compact ? null : (
          <>
            <Badge>{item.reviewState.replaceAll('_', ' ')}</Badge>
            {item.blocked ? <Badge variant="warning">Blocked</Badge> : null}
          </>
        )}
      </div>
      <div className="mt-1.5 flex flex-wrap gap-x-3 gap-y-1 font-mono text-[10.5px] text-muted">
        <span>{mergeRequest.repositoryPath}</span>
        <span>{mergeRequest.sourceBranch} → {mergeRequest.targetBranch}</span>
        <span>{item.roles.join(' · ') || 'synced'}</span>
      </div>
    </button>
  );
}

export default function QueuePage() {
  const router = useRouter();
  const appData = useAppData();
  const [queue, setLocalQueue] = useState(appData.queue);
  const [filter, setFilter] = useState('all');
  const [status, setStatus] = useState(appData.queue ? 'ready' : appData.queueError ? 'error' : 'loading');
  const [error, setError] = useState(appData.queueError);
  const [notice, setNotice] = useState('');
  const [mutation, setMutation] = useState(null);
  const [dragId, setDragId] = useState(null);
  const [expandedGroups, setExpandedGroups] = useState(() => new Set());
  const [groupComposerOpen, setGroupComposerOpen] = useState(false);
  const [groupName, setGroupName] = useState('');
  const [selectedIds, setSelectedIds] = useState(() => new Set());
  const [availableMergeRequests, setAvailableMergeRequests] = useState([]);
  const [groupCandidatesLoading, setGroupCandidatesLoading] = useState(false);

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
    if (appData.queue) {
      setLocalQueue(appData.queue);
      setStatus('ready');
    }
  }, [appData.queue]);

  useEffect(() => {
    if (!notice) return undefined;
    const timer = window.setTimeout(() => setNotice(''), 4000);
    return () => window.clearTimeout(timer);
  }, [notice]);

  async function openGroupComposer() {
    setGroupComposerOpen(true);
    if (availableMergeRequests.length) return;
    setGroupCandidatesLoading(true);
    setError(null);
    try {
      const response = await fetchOpenMergeRequests({ role: 'all', limit: 100 });
      setAvailableMergeRequests(response.items);
    } catch (nextError) {
      setError(nextError);
    } finally {
      setGroupCandidatesLoading(false);
    }
  }

  function toggleSelected(id) {
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function createGroup(event) {
    event.preventDefault();
    if (!groupName.trim() || selectedIds.size < 2 || !queue) return;
    setMutation({ type: 'create-group' });
    setError(null);
    try {
      await createMergeRequestGroup({
        name: groupName.trim(),
        mergeRequestIds: [...selectedIds],
        version: queue.groupVersion,
      });
      updateQueue(await appData.refreshQueue());
      setGroupName('');
      setSelectedIds(new Set());
      setGroupComposerOpen(false);
      setNotice('MR group created and added to your queue.');
    } catch (nextError) {
      if (nextError.status === 409) await reload();
      setError(nextError);
    } finally {
      setMutation(null);
    }
  }

  async function removeGroup(groupId) {
    if (!queue) return;
    setMutation({ type: 'delete-group', groupId });
    setError(null);
    try {
      await deleteMergeRequestGroup(groupId, queue.groupVersion);
      updateQueue(await appData.refreshQueue());
      setNotice('Group removed. Its open MRs are back in the queue.');
    } catch (nextError) {
      if (nextError.status === 409) await reload();
      setError(nextError);
    } finally {
      setMutation(null);
    }
  }

  async function moveItem(fromId, toId) {
    if (!queue || mutation || !fromId || fromId === toId) return;
    const snapshot = queue;
    const items = [...queue.items];
    const fromIndex = items.findIndex((item) => item.id === fromId);
    const toIndex = items.findIndex((item) => item.id === toId);
    if (fromIndex < 0 || toIndex < 0) return;
    const [moved] = items.splice(fromIndex, 1);
    items.splice(toIndex, 0, moved);
    updateQueue({ ...queue, items });
    setMutation({ type: 'reorder' });
    setError(null);
    try {
      updateQueue(await reorderEnrichedMergeQueue({
        orderedItemIds: items.map((item) => item.id),
        version: snapshot.version,
      }));
    } catch (nextError) {
      if (nextError.status === 409) {
        await reload();
        setNotice('Queue changed in another session. The latest order is shown.');
      } else {
        updateQueue(snapshot);
        setError(nextError);
      }
    } finally {
      setMutation(null);
      setDragId(null);
    }
  }

  function toggleGroup(groupId) {
    setExpandedGroups((current) => {
      const next = new Set(current);
      if (next.has(groupId)) next.delete(groupId);
      else next.add(groupId);
      return next;
    });
  }

  const visibleItems = queue?.items.filter((item) => matchesFilter(item, filter)) || [];

  return (
    <div className="mx-auto max-w-[1380px]">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <Overline>GitLab workspace</Overline>
          <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">
            Merge requests
          </h1>
          <p className="mt-2 max-w-[72ch] text-[15px] text-muted">
            Review synchronized open work, reorder priorities, or group related MRs as one review unit.
          </p>
        </div>
        <Button onClick={openGroupComposer}>
          <Layers3 className="h-4 w-4" aria-hidden="true" />
          Group MRs
        </Button>
      </div>

      {groupComposerOpen ? (
        <form onSubmit={createGroup} className="mb-6 border border-border-2 bg-surface">
          <div className="flex items-center justify-between border-b border-border bg-surface-2 px-4 py-3">
            <div>
              <h2 className="font-display text-[17px] font-bold text-fg">Create a review group</h2>
              <p className="mt-0.5 text-[12px] text-muted">Pick at least two synchronized MRs. An MR can belong to multiple groups.</p>
            </div>
            <Button type="button" variant="ghost" size="xs" aria-label="Close group composer" onClick={() => setGroupComposerOpen(false)}>
              <X className="h-4 w-4" />
            </Button>
          </div>
          <div className="grid gap-4 p-4 lg:grid-cols-[280px_minmax(0,1fr)]">
            <div>
              <label htmlFor="group-name" className="mb-1.5 block font-mono text-[10px] uppercase tracking-[0.08em] text-muted">
                Group name
              </label>
              <Input id="group-name" value={groupName} onChange={(event) => setGroupName(event.target.value)} placeholder="Auth V2 rollout" />
              <p className="mt-2 font-mono text-[10px] text-muted">{selectedIds.size} MRs selected</p>
              <Button className="mt-4 w-full" disabled={!groupName.trim() || selectedIds.size < 2 || mutation?.type === 'create-group'}>
                <Plus className="h-4 w-4" />
                {mutation?.type === 'create-group' ? 'Creating…' : 'Create group'}
              </Button>
            </div>
            <div className="max-h-[310px] overflow-y-auto border border-border">
              {groupCandidatesLoading ? <LoadingState label="Loading synchronized MRs" /> : null}
              {!groupCandidatesLoading && availableMergeRequests.length === 0 ? (
                <p className="p-6 text-center text-[13px] text-muted">At least two open synchronized MRs are required.</p>
              ) : availableMergeRequests.map((item) => (
                <label key={item.mergeRequest.id} className="flex cursor-pointer items-center gap-3 border-b border-border px-3 py-2.5 last:border-b-0 hover:bg-[var(--ink-wash)]">
                  <input
                    type="checkbox"
                    className="h-4 w-4 accent-[var(--accent)]"
                    checked={selectedIds.has(item.mergeRequest.id)}
                    onChange={() => toggleSelected(item.mergeRequest.id)}
                  />
                  <span className="font-mono text-[11px] font-semibold text-accent-ink">!{item.mergeRequest.changeNumber}</span>
                  <span className="min-w-0 flex-1 truncate text-[13px] text-fg">{item.mergeRequest.title}</span>
                  <span className="hidden font-mono text-[10px] text-muted sm:block">{item.mergeRequest.repositoryPath}</span>
                </label>
              ))}
            </div>
          </div>
        </form>
      ) : null}

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 border-y border-border py-4">
        <Tabs items={filters.map((item) => ({
          ...item,
          label: item.key === 'all' && queue ? `All · ${queue.items.length}` : item.label,
        }))} activeKey={filter} onTabChange={setFilter} variant="segment" />
        <span className="font-mono text-[10px] uppercase tracking-[0.08em] text-muted">
          {mutation?.type === 'reorder' ? 'Saving order…' : 'Drag rows to reorder'}
        </span>
      </div>

      {notice ? <p role="status" className="mb-4 border border-tertiary bg-surface px-4 py-3 text-[13px] text-muted">{notice}</p> : null}
      {error && status !== 'error' ? <ErrorState className="mb-4" title="Queue operation failed" error={error} /> : null}
      {status === 'loading' ? <LoadingState label="Loading merge request queue" /> : null}
      {status === 'error' ? <ErrorState title="Could not load merge request queue" error={error} onRetry={reload} /> : null}
      {status === 'ready' && visibleItems.length === 0 ? (
        <EmptyState
          title={queue?.items.length ? 'No matching queue items' : 'Queue empty'}
          description={queue?.items.length ? 'Choose another filter.' : 'Arnold adds open GitLab work during synchronization.'}
        />
      ) : null}

      <div className="flex flex-col gap-2.5">
        {visibleItems.map((item) => {
          const isGroup = item.targetType === 'GROUP';
          const expanded = isGroup && expandedGroups.has(item.targetId);
          return (
            <article
              key={item.id}
              draggable={!mutation}
              onDragStart={() => setDragId(item.id)}
              onDragEnd={() => setDragId(null)}
              onDragOver={(event) => event.preventDefault()}
              onDrop={() => moveItem(dragId, item.id)}
              className={`border bg-surface transition-colors hover:border-accent ${
                isGroup ? 'border-border-2 bg-[var(--ink-wash)]' : item.blocked ? 'border-border border-l-[3px] border-l-accent' : 'border-border'
              }`}
            >
              <div className="flex items-center gap-3 px-4 py-3">
                <GripVertical className="h-4 w-4 flex-none cursor-grab text-faint" aria-hidden="true" />
                {isGroup ? (
                  <>
                    <button type="button" onClick={() => toggleGroup(item.targetId)} className="grid h-7 w-7 flex-none place-items-center text-muted hover:text-accent" aria-label={`${expanded ? 'Collapse' : 'Expand'} ${item.group.name}`}>
                      <ChevronRight className={`h-4 w-4 transition-transform ${expanded ? 'rotate-90' : ''}`} />
                    </button>
                    <Badge variant="critical">Set</Badge>
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-[14px] font-semibold text-fg">
                        {item.group.name}
                        <span className="ml-2 font-mono text-[10.5px] font-normal text-muted">{item.group.members.length} MRs</span>
                      </div>
                      <div className="mt-1 font-mono text-[10.5px] text-muted">Shared review context · processed as a unit</div>
                    </div>
                    {item.blocked ? <Badge variant="warning">Blocked</Badge> : null}
                    <Button
                      variant="ghost"
                      size="xs"
                      aria-label={`Delete ${item.group.name}`}
                      disabled={mutation?.type === 'delete-group' && mutation.groupId === item.targetId}
                      onClick={() => removeGroup(item.targetId)}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </>
                ) : (
                  <MergeRequestIdentity item={item.mergeRequest} onOpen={(mergeRequest) => router.push(reviewHref(mergeRequest))} />
                )}
              </div>
              {isGroup && expanded ? (
                <div className="border-t border-border bg-surface">
                  {item.group.members.map((member) => (
                    <div key={member.mergeRequest.id} className="flex items-center gap-3 border-b border-border px-4 py-3 pl-16 last:border-b-0">
                      <MergeRequestIdentity compact item={member} onOpen={(mergeRequest) => router.push(reviewHref(mergeRequest))} />
                    </div>
                  ))}
                </div>
              ) : null}
            </article>
          );
        })}
      </div>
    </div>
  );
}
