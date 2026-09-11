'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { ArrowUp, ArrowDown, ChevronRight, GripVertical, Layers3, Plus, RefreshCw, Trash2, X } from 'lucide-react';
import { SearchInput } from '@/components/ui/SearchInput';
import { queueSection, queueMatches, queueMembers, reviewLabel, blockerLabel, relativeAge } from './queue-inbox.mjs';
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
  if (filter === 'blocked') return item.blocked || leafSummaries(item).some((member) => blockerLabel(member));
  if (filter === 'ready-for-merge') {
    return leafSummaries(item).some((member) => member.reviewState === 'READY_FOR_MERGE' && !member.mergeRequest.draft && !blockerLabel(member));
  }
  if (filter === 'all') return true;
  return leafSummaries(item).some((member) => member.roles.includes(filter));
}

function reviewHref(mergeRequest) {
  return `/review?provider=GITLAB&projectId=${encodeURIComponent(mergeRequest.repositoryId)}&iid=${mergeRequest.changeNumber}`;
}

function MergeRequestIdentity({ item, now }) {
  const mr = item.mergeRequest;
  const blocker = blockerLabel(item);
  return (
    <div className="grid min-w-0 flex-1 gap-3 md:grid-cols-[minmax(0,1fr)_180px_70px]">
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <Link href={reviewHref(mr)} className="truncate text-[14px] font-semibold text-fg hover:text-accent-ink focus-visible:outline-2 focus-visible:outline-accent">{mr.title}</Link>
          {mr.draft ? <Badge>Draft</Badge> : null}
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-[12px] text-muted">
          <span className="font-mono text-[11px]">{mr.repositoryPath} <span className="text-accent-ink">!{mr.changeNumber}</span></span>
          <span>· {mr.authorName || 'Unknown author'}</span>
          {mr.labels.slice(0, 2).map((label) => <span key={label} className="border border-border px-1.5 text-[10px]">{label}</span>)}
        </div>
        <div className="mt-1 truncate font-mono text-[10px] text-faint" title={`${mr.sourceBranch} → ${mr.targetBranch}`}>{mr.sourceBranch} → {mr.targetBranch}</div>
        <details className="mt-2 text-[12px] text-muted">
          <summary className="w-fit cursor-pointer text-accent-ink focus-visible:outline-accent">Quick preview</summary>
          <div className="mt-2 border-l border-border pl-3">
            <p className="whitespace-pre-wrap break-words">{mr.description || 'No description provided.'}</p>
            <p className="mt-2">Reviewers: {mr.reviewerNames.join(', ') || 'Unassigned'}</p>
            <p className="mt-1">Last synced: {mr.lastSyncedAt ? new Date(mr.lastSyncedAt).toLocaleString() : 'Unknown'}</p>
            <Link href={reviewHref(mr)} className="mt-2 inline-block text-accent-ink underline">Open review →</Link>
          </div>
        </details>
      </div>
      <div className="text-[12px]">
        <div className={blocker ? 'font-medium text-accent-ink' : 'font-medium text-fg'}>{blocker || reviewLabel(item.reviewState)}</div>
        <div className="mt-1 truncate text-muted" title={mr.reviewerNames.join(', ')}>{mr.reviewerNames.length ? `Reviewers: ${mr.reviewerNames.join(', ')}` : 'No reviewers assigned'}</div>
        {blocker ? <div className="mt-1 text-[10px] text-muted">{reviewLabel(item.reviewState)}</div> : null}
      </div>
      <div className="font-mono text-[11px] text-muted">
        <time title={new Date(mr.providerUpdatedAt).toLocaleString()} dateTime={new Date(mr.providerUpdatedAt).toISOString()}>{relativeAge(mr.providerUpdatedAt, now)}</time>
        {now - mr.lastSyncedAt > 15 * 60_000 ? <div className="mt-1 text-[10px] text-tertiary-ink" title="GitLab data was last synchronized more than 15 minutes ago">Stale sync</div> : null}
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
  const [filter, setFilter] = useState('all');
  const [search, setSearch] = useState('');
  const [repository, setRepository] = useState('');
  const [label, setLabel] = useState('');
  const [sort, setSort] = useState('updated');
  const [collapsedSections, setCollapsedSections] = useState(new Set());
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

  const items = queue?.items || [];
  const members = [...new Map(items.flatMap(queueMembers).map((member) => [member.mergeRequest.id, member])).values()];
  const repositories = [...new Set(members.map((member) => member.mergeRequest.repositoryPath))].sort();
  const labels = [...new Set(members.flatMap((member) => member.mergeRequest.labels))].sort();
  const visibleItems = items.filter((item) => matchesFilter(item, filter) && queueMatches(item, search, repository, label));
  const manualOrder = sort === 'manual' && filter === 'all' && !search && !repository && !label;
  if (sort !== 'manual') visibleItems.sort((a, b) => {
    const latest = (item) => Math.max(...queueMembers(item).map((member) => member.mergeRequest.providerUpdatedAt));
    return sort === 'oldest' ? latest(a) - latest(b) : latest(b) - latest(a);
  });
  const sectionNames = sort === 'manual' ? ['Your priority order'] : ['Blocked', 'Ready to merge', 'Reviewing', 'Authored', 'Other work', 'Drafts', 'Review groups'];
  const sections = sectionNames.map((name) => ({ name, items: visibleItems.filter((item) => sort === 'manual' || queueSection(item) === name) })).filter((section) => section.items.length);
  function clearFilters() { setSearch(''); setRepository(''); setLabel(''); setFilter('all'); }


  return (
    <div className="mx-auto max-w-[1380px]">
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
          <Button variant="ghost" disabled={status === 'loading' || Boolean(mutation)} onClick={reload} aria-label="Refresh merge requests"><RefreshCw className={`h-4 w-4 ${status === 'loading' ? 'animate-spin' : ''}`} /></Button>
          <Button variant="secondary" onClick={openGroupComposer}>
          <Layers3 className="h-4 w-4" aria-hidden="true" />
          Group MRs
        </Button>
        </div>
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

      <div className="mb-5 border border-border bg-surface">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-3">
          <Tabs className="max-w-full flex-wrap [&_button]:whitespace-nowrap" items={filters.map((item) => ({ ...item, label: `${item.label} · ${members.filter((member) => matchesFilter({ targetType: 'MERGE_REQUEST', mergeRequest: member, blocked: member.blocked }, item.key)).length}` }))} activeKey={filter} onTabChange={setFilter} variant="segment" />
          <span className="font-mono text-[10px] text-muted">{members.length} open MRs · {items.filter((item) => item.targetType === 'GROUP').length} groups</span>
        </div>
        <div className="flex flex-wrap gap-2 p-3">
          <SearchInput className="min-w-[180px] flex-1" aria-label="Search merge requests" placeholder="Search title, !ID, author, branch…" value={search} onChange={(event) => setSearch(event.target.value)} />
          <select aria-label="Repository" value={repository} onChange={(event) => setRepository(event.target.value)} className="h-9 max-w-full border border-border bg-surface px-3 text-[12px] text-fg focus-visible:outline-accent"><option value="">All repositories</option>{repositories.map((value) => <option key={value}>{value}</option>)}</select>
          <select aria-label="Label" value={label} onChange={(event) => setLabel(event.target.value)} className="h-9 border border-border bg-surface px-3 text-[12px] text-fg focus-visible:outline-accent"><option value="">All labels</option>{labels.map((value) => <option key={value}>{value}</option>)}</select>
          <select aria-label="Sort merge requests" value={sort} onChange={(event) => setSort(event.target.value)} className="h-9 border border-border bg-surface px-3 text-[12px] text-fg focus-visible:outline-accent"><option value="updated">Recently updated</option><option value="oldest">Oldest updated</option><option value="manual">Priority order</option></select>
          {search || repository || label || filter !== 'all' ? <Button variant="ghost" onClick={clearFilters}>Clear filters</Button> : null}
        </div>
      </div>
      {sort === 'manual' ? <p className="mb-3 text-[12px] text-muted">{manualOrder ? 'Drag rows or use the arrow buttons to save your priority order.' : 'Clear filters and select All to change priority order.'}</p> : null}

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

      <div className="flex flex-col gap-5">
        {sections.map((section) => <section key={section.name} className="border border-border bg-surface">
          <button type="button" aria-expanded={!collapsedSections.has(section.name)} onClick={() => setCollapsedSections((current) => { const next = new Set(current); if (next.has(section.name)) next.delete(section.name); else next.add(section.name); return next; })} className="flex w-full items-center gap-2 bg-surface-2 px-4 py-3 text-left focus-visible:outline-2 focus-visible:outline-accent">
            <ChevronRight className={`h-3.5 w-3.5 text-muted ${collapsedSections.has(section.name) ? '' : 'rotate-90'}`} />
            <h2 className="font-mono text-[11px] font-semibold uppercase tracking-[0.08em] text-fg">{section.name}</h2>
            <span className="rounded-full border border-border px-2 font-mono text-[10px] text-muted">{section.items.length}</span>
          </button>
          {!collapsedSections.has(section.name) && <div>
          <div className="hidden border-t border-border px-4 py-2 font-mono text-[9px] uppercase tracking-widest text-faint md:grid md:grid-cols-[minmax(0,1fr)_180px_70px] md:gap-3"><span>Merge request</span><span>Review / blockers</span><span>Updated</span></div>
        {section.items.map((item) => {
          const isGroup = item.targetType === 'GROUP';
          const expanded = isGroup && expandedGroups.has(item.targetId);
          return (
            <article
              key={item.id}
              draggable={manualOrder && !mutation}
              onDragStart={() => setDragId(item.id)}
              onDragEnd={() => setDragId(null)}
              onDragOver={(event) => event.preventDefault()}
              onDrop={() => manualOrder && moveItem(dragId, item.id)}
              className={`border-t bg-surface transition-colors hover:bg-[var(--ink-wash)] ${
                isGroup ? 'border-border-2 bg-[var(--ink-wash)]' : item.blocked ? 'border-border border-l-[3px] border-l-accent' : 'border-border'
              }`}
            >
              <div className="flex items-center gap-3 px-4 py-3">
                {manualOrder ? <div className="flex flex-none items-center gap-1"><GripVertical className="h-4 w-4 cursor-grab text-faint" aria-hidden="true" /><div className="flex flex-col"><Button size="xs" variant="ghost" aria-label="Move item up" disabled={Boolean(mutation) || items[0].id === item.id} onClick={() => moveItem(item.id, items[items.findIndex((entry) => entry.id === item.id) - 1]?.id)}><ArrowUp className="h-3 w-3" /></Button><Button size="xs" variant="ghost" aria-label="Move item down" disabled={Boolean(mutation) || items.at(-1).id === item.id} onClick={() => moveItem(item.id, items[items.findIndex((entry) => entry.id === item.id) + 1]?.id)}><ArrowDown className="h-3 w-3" /></Button></div></div> : null}
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
                  <MergeRequestIdentity now={now} item={item.mergeRequest}  />
                )}
              </div>
              {isGroup && expanded ? (
                <div className="border-t border-border bg-surface">
                  {item.group.members.map((member) => (
                    <div key={member.mergeRequest.id} className="flex items-center gap-3 border-b border-border px-4 py-3 pl-16 last:border-b-0">
                      <MergeRequestIdentity now={now} item={member}  />
                    </div>
                  ))}
                </div>
              ) : null}
            </article>
          );
        })}
          </div>}
        </section>)}
      </div>
    </div>
  );
}
