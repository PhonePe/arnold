'use client';

/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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

import React from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { analysisEntry, reviewHref as reviewHrefOf } from '@/lib/analysis-navigation.mjs';
import { changeNumberLabel } from '@/lib/repository-sync.mjs';
import { reviewLabel, blockerLabel, relativeAge } from './queue-inbox.mjs';

export function mergeRequestReviewHref(mergeRequest) {
  return reviewHrefOf({ provider: mergeRequest.provider || 'GITLAB', projectId: mergeRequest.repositoryId, iid: mergeRequest.changeNumber });
}

export function AnalysisButton({ item, className = '' }) {
  const router = useRouter();
  const terminalState = ['closed', 'merged'].includes(item.mergeRequest.state?.toLowerCase())
    ? item.mergeRequest.state
    : item.reviewState;
  const action = analysisEntry(terminalState, item.mergeRequest.id);
  if (!action) return null;
  return <Button className={`relative z-10 flex-none ${className}`} variant="secondary" size="xs" onClick={() => router.push(action.href)}>{action.label}</Button>;
}

export function MergeRequestIdentity({ item, now, compact = false, showProvider = false }) {
  const mr = item.mergeRequest;
  const state = mr.state?.toLowerCase();
  const merged = state === 'merged';
  const closed = state === 'closed';
  const timestamp = merged ? mr.mergedAt : closed && mr.closedAt ? mr.closedAt : mr.providerUpdatedAt;
  const blocker = merged || closed ? '' : blockerLabel(item);
  const stateText = merged ? 'Merged' : closed ? 'Closed' : blocker || reviewLabel(item.reviewState);
  return (
    <div className={`grid min-w-0 flex-1 gap-3 ${compact ? 'grid-cols-[minmax(0,1fr)_auto]' : 'grid-cols-[minmax(0,1fr)_65px] xl:grid-cols-[minmax(0,1fr)_160px_65px]'}`}>
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <Link href={mergeRequestReviewHref(mr)} className={`truncate text-[14px] font-semibold text-fg hover:text-accent-ink focus-visible:outline-2 focus-visible:outline-accent ${compact ? '' : 'after:absolute after:inset-0'}`}>{mr.title}</Link>
          {mr.draft ? <Badge>Draft</Badge> : null}
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-[12px] text-muted">
          {showProvider ? <span className="border border-border px-1.5 font-mono text-[10px] uppercase">{mr.provider === 'GITHUB' ? 'GitHub' : 'GitLab'}</span> : null}
          <span className="font-mono text-[11px]">{mr.repositoryPath} <span className="text-accent-ink">{changeNumberLabel(mr)}</span></span>
          <span>· {mr.authorName || 'Unknown author'}</span>
          {mr.labels.slice(0, 2).map((label) => <span key={label} className="border border-border px-1.5 text-[10px]">{label}</span>)}
        </div>
        {!compact ? <p className="mt-1 text-[11px] text-muted xl:hidden">{stateText}</p> : null}
        {compact ? <p className={`mt-2 text-[11px] ${merged ? 'text-ok' : blocker ? 'text-accent-ink' : 'text-muted'}`}>{stateText}</p> : null}
      </div>
      {!compact && <div className="hidden text-[12px] xl:block">
        <div className={blocker ? 'font-medium text-accent-ink' : 'font-medium text-fg'}>{stateText}</div>
        <div className="mt-1 truncate text-muted" title={mr.reviewerNames.join(', ')}>{mr.reviewerNames.length ? `Reviewers: ${mr.reviewerNames.join(', ')}` : 'No reviewers assigned'}</div>
        {blocker ? <div className="mt-1 text-[10px] text-muted">{reviewLabel(item.reviewState)}</div> : null}
      </div>}
      <div className="font-mono text-[11px] text-muted">
        <time title={timestamp ? new Date(timestamp).toLocaleString() : undefined} dateTime={timestamp ? new Date(timestamp).toISOString() : undefined}>{relativeAge(timestamp, now)}</time>
        {!merged && !closed ? <div className="mt-1 text-[10px] text-muted">
          <span className="whitespace-nowrap">Last synced</span>
          <div title={mr.lastSyncedAt ? new Date(mr.lastSyncedAt).toLocaleString() : undefined}>{mr.lastSyncedAt ? relativeAge(mr.lastSyncedAt, now) : 'Not yet'}</div>
        </div> : null}
      </div>
    </div>
  );
}
