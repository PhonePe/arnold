'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { ErrorState } from '@/components/ui/ErrorState';
import { DiscussionList } from '@/components/composite/DiscussionList';
import { publishReview } from '@/lib/api';

export function ReviewPublishBar({ provider = 'GITLAB', projectId, changeNumber, discussions, enabled = false, onPublished, onChanged, className = '', ...props }) {
  const [summary, setSummary] = useState('');
  const [status, setStatus] = useState('idle');
  const [error, setError] = useState(null);
  const [expanded, setExpanded] = useState(false);
  const drafts = discussions.filter((discussion) => discussion.state === 'draft');
  const draftCount = drafts.length;

  if (draftCount === 0) return null;

  async function publish(reviewerState) {
    setStatus(reviewerState);
    setError(null);
    try {
      const updated = await publishReview({
        projectId: Number(projectId),
        mrIid: Number(changeNumber),
        summary: summary.trim() || null,
        reviewerState,
      });
      setSummary('');
      setStatus('idle');
      onPublished?.(updated);
    } catch (nextError) {
      setError(nextError);
      setStatus('idle');
    }
  }

  return (
    <aside className={`sticky bottom-0 z-10 border border-border-2 bg-surface p-4 shadow-[0_14px_38px_rgba(28,26,22,0.16)] ${className}`} {...props}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <button className="text-left" aria-expanded={expanded} onClick={() => setExpanded((value) => !value)}><p className="font-display text-base font-bold text-fg">Your review · {draftCount}</p><p className="font-mono text-[11px] text-muted">{expanded ? 'Hide' : 'Review'} pending comments</p></button>
        <div className="flex flex-wrap gap-2"><Button size="sm" variant="secondary" disabled={!enabled || status !== 'idle'} onClick={() => publish('reviewed')}>{status === 'reviewed' ? 'Publishing…' : 'Comment'}</Button><Button size="sm" disabled={!enabled || status !== 'idle'} onClick={() => publish('approved')}>{status === 'approved' ? 'Approving…' : 'Approve'}</Button><Button size="sm" variant="secondary" disabled={!enabled || status !== 'idle'} onClick={() => publish('requested_changes')}>{status === 'requested_changes' ? 'Publishing…' : 'Request changes'}</Button></div>
      </div>
      {expanded ? <div className="mt-3 max-h-72 overflow-auto border border-border"><DiscussionList discussions={drafts} provider={provider} projectId={projectId} changeNumber={changeNumber} enabled={enabled} onChanged={onChanged} /></div> : null}
      <label className="mt-3 block font-mono text-[10px] uppercase text-muted">Optional review summary</label>
      <textarea className="mt-1 min-h-16 w-full rounded-none border border-border-2 bg-surface-2 p-2 text-sm text-fg focus:border-accent focus:outline-none" value={summary} onChange={(event) => setSummary(event.target.value)} disabled={status !== 'idle'} />
      {error ? <ErrorState className="mt-2" title="Could not publish review" error={error} /> : null}
    </aside>
  );
}
