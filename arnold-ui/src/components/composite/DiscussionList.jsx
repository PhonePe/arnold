'use client';

import { EmptyState } from '@/components/ui/EmptyState';
import { StaleDataNotice } from '@/components/ui/StaleDataNotice';

export function DiscussionList({ discussions, className = '', ...props }) {
  if (discussions.length === 0) return <EmptyState className={className} title="No synchronized discussions" description="Arnold has no discussion records for this merge request." {...props} />;
  return (
    <div className={`flex flex-col gap-3 ${className}`} {...props}>
      {discussions.map((discussion) => (
        <article key={discussion.id} className="border border-border bg-surface p-4">
          <div className="flex flex-wrap items-center gap-2 font-mono text-[10px] uppercase text-muted"><span>Author {discussion.authorId || 'unknown'}</span><span>·</span><span>{discussion.source}</span><span>·</span><span>{discussion.resolved ? 'Resolved' : discussion.state}</span></div>
          <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-fg">{discussion.body}</p>
          <div className="mt-3 font-mono text-[10px] text-muted">Provider updated {new Date(discussion.providerUpdatedAt).toLocaleString()} · Arnold synced {new Date(discussion.lastSyncedAt).toLocaleString()}</div>
          <StaleDataNotice className="mt-3" syncedAt={discussion.lastSyncedAt} />
        </article>
      ))}
    </div>
  );
}
