'use client';

import React, { useEffect, useState } from 'react';
import { Plus, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { useAppData } from '@/components/layout/AppLayout';
import { createMergeRequestGroup, fetchOpenMergeRequests, resolveMergeRequest } from '@/lib/api';

export function GroupComposer({ onClose, onCreated }) {
  const appData = useAppData();
  const [groupName, setGroupName] = useState('');
  const [selectedIds, setSelectedIds] = useState(() => new Set());
  const [availableMergeRequests, setAvailableMergeRequests] = useState([]);
  const [groupCandidatesLoading, setGroupCandidatesLoading] = useState(true);
  const [mrUrl, setMrUrl] = useState('');
  const [addingUrl, setAddingUrl] = useState(false);
  const [urlError, setUrlError] = useState('');
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    const controller = new AbortController();
    fetchOpenMergeRequests({ role: 'all', limit: 100, signal: controller.signal })
      .then((response) => setAvailableMergeRequests(response.items))
      .catch((nextError) => !controller.signal.aborted && setError(nextError))
      .finally(() => !controller.signal.aborted && setGroupCandidatesLoading(false));
    return () => controller.abort();
  }, []);

  async function addByUrl() {
    const url = mrUrl.trim();
    if (!url) return;
    setAddingUrl(true);
    setUrlError('');
    try {
      const mergeRequest = await resolveMergeRequest(url);
      setAvailableMergeRequests((current) => current.some((item) => item.mergeRequest.id === mergeRequest.id)
        ? current : [{ mergeRequest, roles: [] }, ...current]);
      setSelectedIds((current) => new Set(current).add(mergeRequest.id));
      setMrUrl('');
    } catch (nextError) {
      setUrlError(nextError.message || 'Could not find that merge request.');
    } finally {
      setAddingUrl(false);
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
    if (!groupName.trim() || selectedIds.size < 2) return;
    setCreating(true);
    setError(null);
    try {
      const result = await createMergeRequestGroup({
        name: groupName.trim(),
        mergeRequestIds: [...selectedIds],
        version: appData.queue?.groupVersion ?? 0,
      });
      await appData.refreshQueue().catch(() => {});
      onCreated(result);
    } catch (nextError) {
      setError(nextError);
      setCreating(false);
    }
  }

  return (
    <>
    <form onSubmit={createGroup} className="mb-6 border border-border-2 bg-surface">
      <div className="flex items-center justify-between border-b border-border bg-surface-2 px-4 py-3">
        <div>
          <h2 className="font-display text-[17px] font-bold text-fg">Create a review group</h2>
          <p className="mt-0.5 text-[12px] text-muted">Pick at least two synchronized MRs. An MR can belong to multiple groups.</p>
        </div>
        <Button type="button" variant="ghost" size="xs" aria-label="Close group composer" onClick={() => onClose()}>
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
          <Button className="mt-4 w-full" disabled={!groupName.trim() || selectedIds.size < 2 || creating}>
            <Plus className="h-4 w-4" />
            {creating ? 'Creating…' : 'Create group'}
          </Button>
        </div>
        <div>
        <div className="mb-3 flex gap-2">
          <Input aria-label="GitLab MR URL" value={mrUrl} onChange={(event) => setMrUrl(event.target.value)}
            onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); addByUrl(); } }}
            placeholder="Paste a GitLab MR URL to add it, even if it is not assigned to you" />
          <Button type="button" variant="secondary" disabled={!mrUrl.trim() || addingUrl} onClick={addByUrl}>
            {addingUrl ? 'Adding…' : 'Add MR'}
          </Button>
        </div>
        {urlError ? <p role="alert" className="mb-3 text-[12px] text-accent-ink">{urlError}</p> : null}
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
      </div>
    </form>
      {error ? <ErrorState className="mb-4" title="Could not create group" error={error} /> : null}
    </>
  );
}
