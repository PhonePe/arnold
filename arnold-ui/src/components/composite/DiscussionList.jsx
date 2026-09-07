'use client';

import { EmptyState } from '@/components/ui/EmptyState';
import { Markdown } from '@/components/ui/Markdown';
import { DiscussionThread } from '@/components/composite/DiscussionThread';
import { groupDiscussions } from '@/lib/discussions';

export function DiscussionList({ discussions, provider = 'GITLAB', projectId, changeNumber, enabled = false, descending = false, showPosition = false, onChanged, className = '', ...props }) {
  const grouped = groupDiscussions(discussions);
  const threads = descending ? [...grouped].reverse() : grouped;
  if (threads.length === 0) return <EmptyState className={className} title="No discussions" description="There are no comments in this section." {...props} />;
  return (
    <div className={`flex flex-col gap-3 ${className}`} {...props}>
      {threads.map((thread) => thread.notes.every((note) => note.state === 'system')
        ? <div key={thread.id} className="border-l-[3px] border-border-2 bg-[var(--ink-wash)] px-4 py-3"><Markdown>{thread.notes[0].body}</Markdown><p className="mt-1 font-mono text-[10px] text-muted">{thread.notes[0].authorUsername || 'GitLab'} · {new Date(thread.notes[0].createdAt).toLocaleString()}</p></div>
        : <DiscussionThread key={thread.id} thread={thread} provider={provider} projectId={projectId} changeNumber={changeNumber} enabled={enabled} showPosition={showPosition} onChanged={onChanged} />)}
    </div>
  );
}
