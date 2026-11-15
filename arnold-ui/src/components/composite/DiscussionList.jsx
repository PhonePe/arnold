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

import { EmptyState } from '@/components/ui/EmptyState';
import { Markdown } from '@/components/ui/Markdown';
import { DiscussionThread } from '@/components/composite/DiscussionThread';
import { groupDiscussions } from '@/lib/discussions';

export function DiscussionList({ discussions, provider = 'GITLAB', projectId, changeNumber, headSha = null, enabled = false, descending = false, showPosition = false, onChanged, onNavigate, className = '', ...props }) {
  const grouped = groupDiscussions(discussions);
  const threads = descending ? [...grouped].reverse() : grouped;
  if (threads.length === 0) return <EmptyState className={className} title="No discussions" description="There are no comments in this section." {...props} />;
  return (
    <div className={`flex flex-col gap-3 ${className}`} {...props}>
      {threads.map((thread) => thread.notes.every((note) => note.state === 'system')
        ? <div key={thread.id} className="border-l-[3px] border-border-2 bg-[var(--ink-wash)] px-4 py-3"><Markdown>{thread.notes[0].body}</Markdown><p className="mt-1 font-mono text-[10px] text-muted">{thread.notes[0].authorUsername || 'GitLab'} · {new Date(thread.notes[0].createdAt).toLocaleString()}</p></div>
        : <DiscussionThread key={thread.id} thread={thread} provider={provider} projectId={projectId} changeNumber={changeNumber} headSha={headSha} enabled={enabled} showPosition={showPosition} onChanged={onChanged} onNavigate={onNavigate} />)}
    </div>
  );
}
