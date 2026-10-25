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

import { useState } from 'react';
import { ChevronDown, MessageSquare } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ErrorState } from '@/components/ui/ErrorState';
import { DiscussionList } from '@/components/composite/DiscussionList';
import { publishReview } from '@/lib/api';

export function ReviewPublishBar({ provider = 'GITLAB', projectId, changeNumber, discussions, enabled = false, onPublished, onChanged, onNavigate, className = '', ...props }) {
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
        provider,
        projectId: Number(projectId),
        mrIid: Number(changeNumber),
        summary: summary.trim() || null,
        reviewerState,
      }, { provider });
      setSummary('');
      setStatus('idle');
      onPublished?.(updated);
    } catch (nextError) {
      setError(nextError);
      setStatus('idle');
    }
  }

  if (!expanded) {
    return (
      <div className={`pointer-events-none fixed inset-x-0 bottom-4 z-40 flex justify-center px-4 ${className}`} {...props}>
        <button type="button" className="pointer-events-auto flex items-center gap-2 rounded-full border border-border-2 bg-surface/95 py-1.5 pl-3 pr-4 font-mono text-xs font-semibold text-fg shadow-md backdrop-blur hover:bg-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-expanded={false} onClick={() => setExpanded(true)}>
          <span className="grid h-6 w-6 place-items-center rounded-full bg-accent text-on-accent"><MessageSquare className="h-3.5 w-3.5" aria-hidden="true" /></span>
          Your review · {draftCount}
        </button>
      </div>
    );
  }

  return (
    <aside className={`sticky bottom-0 z-40 border border-border-2 bg-surface p-4 shadow-lg ${className}`} {...props}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <button className="text-left" aria-expanded={expanded} onClick={() => setExpanded((value) => !value)}><p className="font-display text-base font-bold text-fg">Your review · {draftCount}</p><p className="font-mono text-[11px] text-muted">{expanded ? 'Hide' : 'Review'} pending comments</p></button>
        <div className="flex flex-wrap items-center gap-2">
          <Button size="sm" variant="secondary" disabled={!enabled || status !== 'idle'} onClick={() => publish('reviewed')}>{status === 'reviewed' ? 'Publishing…' : 'Comment'}</Button><Button size="sm" disabled={!enabled || status !== 'idle'} onClick={() => publish('approved')}>{status === 'approved' ? 'Approving…' : 'Approve'}</Button><Button size="sm" variant="secondary" disabled={!enabled || status !== 'idle'} onClick={() => publish('requested_changes')}>{status === 'requested_changes' ? 'Publishing…' : 'Request changes'}</Button>
          <button type="button" className="grid h-8 w-8 place-items-center border border-border-2 bg-surface-2 text-muted hover:text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label="Minimize review bar" onClick={() => setExpanded(false)}><ChevronDown className="h-4 w-4" aria-hidden="true" /></button>
        </div>
      </div>
      {expanded ? <div className="mt-3 max-h-72 overflow-auto border border-border"><DiscussionList discussions={drafts} provider={provider} projectId={projectId} changeNumber={changeNumber} enabled={enabled} onChanged={onChanged} onNavigate={onNavigate} /></div> : null}
      <label className="mt-3 block font-mono text-[10px] uppercase text-muted">Optional review summary</label>
      <textarea className="mt-1 min-h-16 w-full rounded-none border border-border-2 bg-surface-2 p-2 text-sm text-fg focus:border-accent focus:outline-none" value={summary} onChange={(event) => setSummary(event.target.value)} disabled={status !== 'idle'} />
      {error ? <ErrorState className="mt-2" title="Could not publish review" error={error} /> : null}
    </aside>
  );
}
