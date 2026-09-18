'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ChevronRight, GripVertical, Layers3, GitPullRequest, Eye, GitMerge, PencilLine, Plus, RefreshCw, Trash2, X } from 'lucide-react';
import { SearchInput } from '@/components/ui/SearchInput';
import { inboxSections, queueMatches, queueMembers, reviewLabel, blockerLabel, relativeAge, priorityMembers, priorityMatches, recentMergedMembers, isOpenMember, movePriorityMember } from './queue-inbox.mjs';
import { Overline } from '@/components/ui/Overline';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Badge } from '@/components/ui/Badge';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { useAppData } from '@/components/layout/AppLayout';
import { analysisEntry } from '@/lib/analysis-navigation.mjs';
import {
  createMergeRequestGroup,
  deleteMergeRequestGroup,
  fetchOpenMergeRequests,
  fetchMergedMergeRequests,
  reorderEnrichedMergeQueue,
  reorderPriorityQueue,
} from '@/lib/api';

function reviewHref(mergeRequest) {
  return `/review?provider=GITLAB&projectId=${encodeURIComponent(mergeRequest.repositoryId)}&iid=${mergeRequest.changeNumber}`;
}

function AnalysisButton({ item }) {
  const router = useRouter();
  const terminalState = ['closed', 'merged'].includes(item.mergeRequest.state?.toLowerCase())
    ? item.mergeRequest.state
    : item.reviewState;
  const action = analysisEntry(terminalState, item.mergeRequest.id);
  if (!action) return null;
  return <Button className="relative z-10 flex-none" variant="secondary" size="xs" onClick={() => router.push(action.href)}>{action.label}</Button>;
}

function MergeRequestIdentity({ item, now, compact = false }) {
  const mr = item.mergeRequest;
  const merged = mr.state?.toLowerCase() === 'merged';
  const timestamp = merged ? mr.mergedAt : mr.providerUpdatedAt;
  const blocker = merged ? '' : blockerLabel(item);
  return (
    <div className={`grid min-w-0 flex-1 gap-3 ${compact ? 'grid-cols-[minmax(0,1fr)_auto]' : 'grid-cols-[minmax(0,1fr)_65px] xl:grid-cols-[minmax(0,1fr)_160px_65px]'}`}>
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <Link href={reviewHref(mr)} className={`truncate text-[14px] font-semibold text-fg hover:text-accent-ink focus-visible:outline-2 focus-visible:outline-accent ${compact ? '' : 'after:absolute after:inset-0'}`}>{mr.title}</Link>
          {mr.draft ? <Badge>Draft</Badge> : null}
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-[12px] text-muted">
          <span className="font-mono text-[11px]">{mr.repositoryPath} <span className="text-accent-ink">!{mr.changeNumber}</span></span>
          <span>· {mr.authorName || 'Unknown author'}</span>
          {mr.labels.slice(0, 2).map((label) => <span key={label} className="border border-border px-1.5 text-[10px]">{label}</span>)}
        </div>
        {!compact ? <p className="mt-1 text-[11px] text-muted xl:hidden">{merged ? 'Merged' : blocker || reviewLabel(item.reviewState)}</p> : null}
        {compact ? <p className={`mt-2 text-[11px] ${merged ? 'text-ok' : blocker ? 'text-accent-ink' : 'text-muted'}`}>{merged ? 'Merged' : blocker || reviewLabel(item.reviewState)}</p> : null}
      </div>
      {!compact && <div className="hidden text-[12px] xl:block">
        <div className={blocker ? 'font-medium text-accent-ink' : 'font-medium text-fg'}>{merged ? 'Merged' : blocker || reviewLabel(item.reviewState)}</div>
        <div className="mt-1 truncate text-muted" title={mr.reviewerNames.join(', ')}>{mr.reviewerNames.length ? `Reviewers: ${mr.reviewerNames.join(', ')}` : 'No reviewers assigned'}</div>
        {blocker ? <div className="mt-1 text-[10px] text-muted">{reviewLabel(item.reviewState)}</div> : null}
      </div>}
      <div className="font-mono text-[11px] text-muted">
        <time title={timestamp ? new Date(timestamp).toLocaleString() : undefined} dateTime={timestamp ? new Date(timestamp).toISOString() : undefined}>{relativeAge(timestamp, now)}</time>
        {!merged ? <div className="mt-1 text-[10px] text-muted">
          <span className="whitespace-nowrap">Last synced</span>
          <div title={mr.lastSyncedAt ? new Date(mr.lastSyncedAt).toLocaleString() : undefined}>{mr.lastSyncedAt ? relativeAge(mr.lastSyncedAt, now) : 'Not yet'}</div>
        </div> : null}
      </div>
    </div>
  );
}

export default function QueuePage() {
  const appData = useAppData();
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 60_000);
    return () => window.clearInterval(timer);
  }, []);
  const [queue, setLocalQueue] = useState(appData.queue);
  const [view, setView] = useState('priority');
  const [mergedMembers, setMergedMembers] = useState([]);
  const [mergedStatus, setMergedStatus] = useState('loading');
  const [mergedError, setMergedError] = useState(null);
  const [mergedRefresh, setMergedRefresh] = useState(0);
  const [inboxSection, setInboxSection] = useState('Needs your review');
  const [search, setSearch] = useState('');
  const [repository, setRepository] = useState('');
  const [label, setLabel] = useState('');
  const [sort, setSort] = useState('updated');
  const [status, setStatus] = useState(appData.queue ? 'ready' : appData.queueError ? 'error' : 'loading');
  const [error, setError] = useState(appData.queueError);
  const [notice, setNotice] = useState('');
  const [mutation, setMutation] = useState(null);
  const [dragId, setDragId] = useState(null);
  const [priorityDrag, setPriorityDrag] = useState(null);
  const [priorityDrop, setPriorityDrop] = useState(null);
  const [expandedGroups, setExpandedGroups] = useState(() => new Set());
  const [groupComposerOpen, setGroupComposerOpen] = useState(false);
  const [groupName, setGroupName] = useState('');
  const [selectedIds, setSelectedIds] = useState(() => new Set());
  const [availableMergeRequests, setAvailableMergeRequests] = useState([]);
  const [groupCandidatesLoading, setGroupCandidatesLoading] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    fetchMergedMergeRequests({ signal: controller.signal }).then((members) => {
      if (controller.signal.aborted) return;
      setMergedMembers(members);
      setMergedStatus('ready');
    }).catch((nextError) => {
      if (controller.signal.aborted) return;
      setMergedError(nextError);
      setMergedStatus('error');
    });
    return () => controller.abort();
  }, [mergedRefresh, appData.sync?.lastSucceededAt]);

  function reloadMerged() {
    setMergedStatus('loading');
    setMergedError(null);
    setMergedRefresh((current) => current + 1);
  }

  function updateQueue(nextQueue) {
    setLocalQueue(nextQueue);
    appData.setQueue(nextQueue);
    setStatus('ready');
  }

  async function reload() {
    reloadMerged();
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
    } else if (appData.queueError) {
      setError(appData.queueError);
      setStatus('error');
    }
  }, [appData.queue, appData.queueError]);

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

  async function movePriority(fromId, toId, position) {
    if (!queue || mutation || status !== 'ready' || search || repository || label) return;
    const priorityOrder = movePriorityMember(members, fromId, toId, position);
    if (!priorityOrder) return;
    const snapshot = queue;
    updateQueue({ ...queue, priorityOrder });
    setMutation({ type: 'priority' });
    setError(null);
    try {
      updateQueue(await reorderPriorityQueue({ orderedItemIds: priorityOrder, version: snapshot.version }));
      setNotice('Your priority order is saved.');
    } catch (nextError) {
      if (nextError.status === 409) {
        await reload();
        setNotice('Queue changed in another session. The latest priorities are shown.');
      } else {
        updateQueue(snapshot);
        setError(nextError);
      }
    } finally {
      setMutation(null);
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

  const items = queue?.items || [];
  const members = priorityMembers(items, queue?.priorityOrder);
  const recentMerged = recentMergedMembers(mergedMembers, now);
  const allMembers = [...members, ...recentMerged];
  const repositories = [...new Set(allMembers.map((member) => member.mergeRequest.repositoryPath))].sort();
  const labels = [...new Set(allMembers.flatMap((member) => member.mergeRequest.labels))].sort();
  const visibleItems = items.filter((item) => (item.targetType === 'GROUP' || isOpenMember(item.mergeRequest)) && queueMatches(item, search, repository, label));
  const manualOrder = view === 'inbox' && sort === 'manual' && inboxSection === 'All open' && !search && !repository && !label;
  if (sort !== 'manual') visibleItems.sort((a, b) => {
    const latest = (item) => Math.max(...queueMembers(item).map((member) => member.mergeRequest.providerUpdatedAt));
    return sort === 'oldest' ? latest(a) - latest(b) : latest(b) - latest(a);
  });
  const matchesSearch = (member) => queueMatches({ targetType: 'MERGE_REQUEST', mergeRequest: member }, search, repository, label);
  const filteredMerged = recentMerged.filter(matchesSearch);
  const tiles = [
    { key: 'author', name: 'Authored', description: 'Your open merge requests', icon: PencilLine },
    { key: 'reviewer', name: 'Reviewing', description: 'Reviews assigned to you', icon: GitPullRequest },
    { key: 'watcher', name: 'Watching', description: 'Assigned and watched work', icon: Eye },
    { key: 'merged', name: 'Merged', description: 'Your work · last 30 days', icon: GitMerge },
  ].map((tile) => ({ ...tile, members: tile.key === 'merged' ? filteredMerged : members
    .filter((member) => priorityMatches(member, tile.key) && matchesSearch(member)) }));
  const canMovePriority = status === 'ready' && !mutation && !search && !repository && !label;
  const sections = inboxSections(visibleItems, filteredMerged, { search, repository, label, sort });
  const selectedSection = sections.find((section) => section.name === inboxSection);
  const inboxStatus = inboxSection === 'Recently merged' ? mergedStatus : status;
  const inboxError = inboxSection === 'Recently merged' ? mergedError : error;
  const retryInbox = inboxSection === 'Recently merged' ? reloadMerged : reload;
  function clearFilters() { setSearch(''); setRepository(''); setLabel(''); }

  const toolbar = (
      <div className="border-b border-border bg-surface">
        <div className="flex flex-wrap gap-2 p-3">
          <SearchInput className="min-w-[180px] flex-1" aria-label="Search merge requests" placeholder="Search title, !ID, author, branch…" value={search} onChange={(event) => setSearch(event.target.value)} />
          <select aria-label="Repository" value={repository} onChange={(event) => setRepository(event.target.value)} className="h-9 max-w-full border border-border bg-surface px-3 text-[12px] text-fg focus-visible:outline-accent"><option value="">All repositories</option>{repositories.map((value) => <option key={value}>{value}</option>)}</select>
          <select aria-label="Label" value={label} onChange={(event) => setLabel(event.target.value)} className="h-9 border border-border bg-surface px-3 text-[12px] text-fg focus-visible:outline-accent"><option value="">All labels</option>{labels.map((value) => <option key={value}>{value}</option>)}</select>
          {view === 'inbox' && inboxSection !== 'Recently merged' && <select aria-label="Sort merge requests" value={sort} onChange={(event) => setSort(event.target.value)} className="h-9 border border-border bg-surface px-3 text-[12px] text-fg focus-visible:outline-accent"><option value="updated">Recently updated</option><option value="oldest">Oldest updated</option><option value="manual">Priority order</option></select>}
          {search || repository || label ? <Button variant="ghost" onClick={clearFilters}>Clear filters</Button> : null}
        </div>
      </div>
  );


  return (
    <div className="w-full">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <Overline>GitLab workspace</Overline>
          <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">
            Merge requests
          </h1>
          <p className="mt-2 max-w-[72ch] text-[15px] text-muted">
            Your review inbox. See what needs attention and keep work moving.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="secondary" disabled={appData.syncBusy || Boolean(mutation)} onClick={appData.startSync}>
            <RefreshCw className={`h-4 w-4 ${appData.syncBusy ? 'animate-spin' : ''}`} aria-hidden="true" />
            {appData.syncBusy ? 'Syncing…' : 'Sync now'}
          </Button>
          {view === 'inbox' && <Button variant="secondary" onClick={openGroupComposer}>
          <Layers3 className="h-4 w-4" aria-hidden="true" />
          Group MRs
        </Button>}
        </div>
      </div>

      <p role="status" className="mb-4 text-xs text-muted">
        {appData.syncBusy ? 'Synchronizing GitLab…' : appData.sync?.lastSucceededAt
          ? `Last synced ${relativeAge(appData.sync.lastSucceededAt, now)}` : 'GitLab has not been synced yet.'}
      </p>
      {appData.syncError || appData.sync?.status === 'FAILED' ? <ErrorState className="mb-4" title="GitLab sync needs attention"
        error={appData.syncError || new Error(appData.sync.failure || 'The last GitLab sync failed. Use Sync now to retry.')} /> : null}

      {view === 'inbox' && groupComposerOpen ? (
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

      <div role="tablist" aria-label="Merge request views" className="mb-5 flex gap-6 border-b border-border">
        {['priority', 'inbox'].map((key, index) => (
          <button key={key} id={`queue-tab-${key}`} type="button" role="tab" aria-selected={view === key}
            aria-controls="queue-panel" tabIndex={view === key ? 0 : -1}
            onClick={() => setView(key)} onKeyDown={(event) => {
              if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
              event.preventDefault();
              const next = event.key === 'Home' ? 'priority' : event.key === 'End' ? 'inbox' : ['priority', 'inbox'][1 - index];
              setView(next);
              document.getElementById(`queue-tab-${next}`)?.focus();
            }}
            className={`border-b-2 px-1 py-3 text-sm font-semibold capitalize focus-visible:outline-2 focus-visible:outline-accent ${view === key ? 'border-accent text-fg' : 'border-transparent text-muted hover:text-fg'}`}>
            {key}
          </button>
        ))}
      </div>

      {view === 'priority' && <div className="mb-5 border border-border">{toolbar}</div>}
      {view === 'priority' ? <p className="mb-3 text-xs text-muted">{search || repository || label ? 'Clear filters to change your priority order.' : 'Your personal priority order. Drag open MRs to any position.'}</p> : null}


      {notice ? <p role="status" className="mb-4 border border-tertiary bg-surface px-4 py-3 text-[13px] text-muted">{notice}</p> : null}
      {error && status !== 'error' ? <ErrorState className="mb-4" title="Queue operation failed" error={error} /> : null}
      <div id="queue-panel" role="tabpanel" aria-labelledby={`queue-tab-${view}`} tabIndex={0} className="focus-visible:outline-accent">
      {view === 'priority' ? <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-2">
        {[0, 1].map((column) => <div key={column} className="contents lg:flex lg:min-w-0 lg:flex-col lg:gap-4">
        {tiles.filter((_, index) => index % 2 === column).map((tile) => {
          const Icon = tile.icon;
          const tileStatus = tile.key === 'merged' ? mergedStatus : status;
          return <section key={tile.key} aria-labelledby={`tile-${tile.key}`} className={`min-w-0 border border-border bg-surface ${{ author: 'order-1', reviewer: 'order-2', watcher: 'order-3', merged: 'order-4' }[tile.key]}`}>
            <header className="flex items-center gap-3 border-b border-border bg-surface-2 px-4 py-4">
              <Icon className={`h-5 w-5 ${tile.key === 'merged' ? 'text-ok' : 'text-accent-ink'}`} aria-hidden="true" />
              <div className="flex-1"><h2 id={`tile-${tile.key}`} className="font-display text-lg font-semibold text-fg">{tile.name}</h2><p className="mt-0.5 text-xs text-muted">{tile.description}</p></div>
              <span className="rounded-full border border-border px-2.5 py-0.5 font-mono text-xs text-muted">{tileStatus === 'ready' ? tile.members.length : '—'}</span>
            </header>
            {tileStatus === 'loading' ? <LoadingState label={`Loading ${tile.name.toLowerCase()} MRs`} /> : tileStatus === 'error' ? <ErrorState title={`Could not load ${tile.name.toLowerCase()} MRs`} error={tile.key === 'merged' ? mergedError : error} onRetry={tile.key === 'merged' ? reloadMerged : reload} /> : tile.members.length ? <div className="max-h-[480px] overflow-y-auto">
              {tile.members.map((member) => <article key={member.mergeRequest.id}
                draggable={canMovePriority && tile.key !== 'merged'}
                onDragStart={(event) => {
                  if (!canMovePriority || tile.key === 'merged') { event.preventDefault(); return; }
                  event.dataTransfer.effectAllowed = 'move';
                  event.dataTransfer.setData('text/plain', member.mergeRequest.id);
                  setPriorityDrag({ id: member.mergeRequest.id, tile: tile.key });
                }}
                onDragEnd={() => { setPriorityDrag(null); setPriorityDrop(null); }}
                onDragOver={(event) => {
                  if (!canMovePriority || priorityDrag?.tile !== tile.key || priorityDrag.id === member.mergeRequest.id) return;
                  event.preventDefault();
                  event.dataTransfer.dropEffect = 'move';
                  const bounds = event.currentTarget.getBoundingClientRect();
                  setPriorityDrop({ id: member.mergeRequest.id, tile: tile.key, position: event.clientY < bounds.top + bounds.height / 2 ? 'before' : 'after' });
                }}
                onDragLeave={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setPriorityDrop(null); }}
                onDrop={(event) => {
                  if (!canMovePriority || priorityDrag?.tile !== tile.key) return;
                  event.preventDefault();
                  const bounds = event.currentTarget.getBoundingClientRect();
                  movePriority(priorityDrag.id, member.mergeRequest.id, event.clientY < bounds.top + bounds.height / 2 ? 'before' : 'after');
                  setPriorityDrag(null);
                  setPriorityDrop(null);
                }}
                className={`relative flex items-center gap-3 border-b border-border px-4 py-3 last:border-b-0 hover:bg-[var(--ink-wash)] ${canMovePriority && tile.key !== 'merged' ? 'cursor-grab active:cursor-grabbing' : ''} ${priorityDrag?.tile === tile.key && priorityDrag.id === member.mergeRequest.id ? 'opacity-50' : ''} ${priorityDrop?.tile === tile.key && priorityDrop.id === member.mergeRequest.id ? priorityDrop.position === 'before' ? 'before:absolute before:inset-x-0 before:top-0 before:h-0.5 before:bg-accent' : 'after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:bg-accent' : ''}`}>
                {tile.key !== 'merged' && <GripVertical className="h-4 w-4 flex-none text-faint" aria-hidden="true" />}
                <MergeRequestIdentity item={member} now={now} compact />
                <AnalysisButton item={member} />
              </article>)}
            </div> : <p className="px-4 py-10 text-center text-sm text-muted">{search || repository || label ? 'No matching merge requests.' : `No ${tile.name.toLowerCase()} merge requests${tile.key === 'merged' ? ' in the last 30 days' : ''}.`}</p>}
          </section>;
        })}
        </div>)}
      </div> : <div className="grid items-start gap-4 lg:grid-cols-[210px_minmax(0,1fr)]">
        <nav aria-label="Inbox sections" className="flex gap-1 overflow-x-auto border border-border bg-surface p-2 lg:sticky lg:top-0 lg:flex-col">
          {sections.map((section) => <button key={section.name} type="button" aria-current={inboxSection === section.name ? 'page' : undefined} onClick={() => setInboxSection(section.name)} className={`flex flex-none items-center justify-between gap-3 whitespace-nowrap border-l-2 px-3 py-2.5 text-left text-[12px] focus-visible:outline-2 focus-visible:outline-accent ${inboxSection === section.name ? 'border-accent bg-[var(--accent-soft)] font-semibold text-accent-ink' : 'border-transparent text-muted hover:bg-[var(--ink-wash)] hover:text-fg'}`}>
            <span>{section.name}</span>
            <span className="font-mono text-[10px]">{(section.name === 'Recently merged' ? mergedStatus : status) === 'ready' ? section.items.length : '—'}</span>
          </button>)}
        </nav>
        <section className="min-w-0 border border-border bg-surface" aria-labelledby="inbox-section-title">
          <header className="flex items-center justify-between gap-3 border-b border-border px-4 py-4">
            <h2 id="inbox-section-title" className="font-display text-lg font-semibold text-fg">{selectedSection.name}</h2>
            <span className="font-mono text-[10px] text-muted">{inboxStatus === 'ready' ? `${selectedSection.items.length} ${inboxSection === 'Review groups' ? 'group' : 'item'}${selectedSection.items.length === 1 ? '' : 's'}` : '—'}{inboxSection === 'Recently merged' ? ' · last 30 days' : ''}</span>
          </header>
          {toolbar}
          {sort === 'manual' && inboxSection !== 'Recently merged' ? <p className="px-4 pb-3 text-xs text-muted">{manualOrder ? 'Drag rows to save your queue order.' : 'Select All open and clear filters to change queue order.'}</p> : null}
          {inboxStatus === 'loading' ? <LoadingState label="Loading merge requests" /> : inboxStatus === 'error' ? <ErrorState title="Could not load merge requests" error={inboxError} onRetry={retryInbox} /> : selectedSection.items.length === 0 ? <EmptyState title="No merge requests here" description={search || repository || label ? 'Try another search or clear the filters.' : 'You are all caught up in this section.'} /> : <div>
          <div className="hidden border-b border-border bg-surface-2 px-4 py-2 font-mono text-[9px] uppercase tracking-widest text-faint xl:grid xl:grid-cols-[minmax(0,1fr)_160px_65px] xl:gap-3"><span>Merge request</span><span>Review / blockers</span><span>{inboxSection === 'Recently merged' ? 'Merged' : 'Updated'}</span></div>
        {selectedSection.items.map((item) => {
          const isGroup = item.targetType === 'GROUP';
          const expanded = isGroup && expandedGroups.has(item.targetId);
          return (
            <article
              key={item.id}
              draggable={manualOrder && !mutation}
              onDragStart={(event) => { event.dataTransfer.effectAllowed = 'move'; event.dataTransfer.setData('text/plain', item.id); setDragId(item.id); }}
              onDragEnd={() => setDragId(null)}
              onDragOver={(event) => { if (manualOrder) event.preventDefault(); }}
              onDrop={(event) => { if (manualOrder) { event.preventDefault(); moveItem(dragId, item.id); } }}
              className={`relative border-t bg-surface transition-colors hover:bg-[var(--ink-wash)] ${
                isGroup ? 'border-border-2 bg-[var(--ink-wash)]' : item.blocked ? 'border-border border-l-[3px] border-l-accent' : 'border-border'
              }`}
            >
              <div className="flex items-center gap-3 px-4 py-3">
                {manualOrder ? <GripVertical className="h-4 w-4 flex-none cursor-grab text-faint" aria-hidden="true" /> : null}
                {isGroup ? (
                  <>
                    <button type="button" onClick={() => toggleGroup(item.targetId)} className="grid h-7 w-7 flex-none place-items-center text-muted hover:text-accent" aria-label={`${expanded ? 'Collapse' : 'Expand'} ${item.group.name}`}>
                      <ChevronRight className={`h-4 w-4 transition-transform ${expanded ? 'rotate-90' : ''}`} />
                    </button>
                    <Layers3 className="h-4 w-4 text-muted" aria-hidden="true" />
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-[14px] font-semibold text-fg">
                        {item.group.name}
                        <span className="ml-2 font-mono text-[10.5px] font-normal text-muted">{item.group.members.length} MRs</span>
                      </div>
                      <div className="mt-1 font-mono text-[10.5px] text-muted">{[...new Set(item.group.members.map((member) => member.mergeRequest.repositoryPath))].join(' · ')} · {item.group.members.filter((member) => blockerLabel(member)).length} blocked</div>
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
                  <><MergeRequestIdentity now={now} item={item.mergeRequest} /><AnalysisButton item={item.mergeRequest} /></>
                )}
              </div>
              {isGroup && expanded ? (
                <div className="border-t border-border bg-surface">
                  {item.group.members.map((member) => (
                    <div key={member.mergeRequest.id} className="relative flex items-center gap-3 border-b border-border px-4 py-3 pl-10 last:border-b-0">
                      <MergeRequestIdentity now={now} item={member} />
                      <AnalysisButton item={member} />
                    </div>
                  ))}
                </div>
              ) : null}
            </article>
          );
        })}
          </div>}
        </section>
      </div>}
      </div>
    </div>
  );
}
