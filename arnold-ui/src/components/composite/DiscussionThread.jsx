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
import { ChevronRight, EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { ErrorState } from '@/components/ui/ErrorState';
import { Markdown } from '@/components/ui/Markdown';
import { DiscussionComposer } from '@/components/composite/DiscussionComposer';
import { applySuggestion, deleteDraftDiscussion, setDiscussionResolution, updateDraftDiscussion } from '@/lib/api';
import { commentAssessment } from '@/lib/comment-assessment.mjs';
import { withoutSuggestionBlocks } from '@/lib/markdown-details.mjs';

function DiscussionNote({ note, provider = 'GITLAB', projectId, changeNumber, headSha, position, onHide, onChanged }) {
  const [editing, setEditing] = useState(false);
  const [body, setBody] = useState(note.body);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const commentBody = note.suggestions.length ? withoutSuggestionBlocks(note.body) : note.body;
  const acceptance = commentAssessment(note, headSha);

  async function save() {
    setBusy(true);
    setError(null);
    try {
      await updateDraftDiscussion(note.draftId, { provider, projectId: Number(projectId), mrIid: Number(changeNumber), body: body.trim() }, { provider });
      setEditing(false);
      onChanged?.();
    } catch (nextError) {
      setError(nextError);
    } finally {
      setBusy(false);
    }
  }

  async function discard() {
    setBusy(true);
    setError(null);
    try {
      await deleteDraftDiscussion(note.draftId, projectId, changeNumber, { provider });
      onChanged?.();
    } catch (nextError) {
      setError(nextError);
      setBusy(false);
    }
  }

  async function apply(suggestionId) {
    setBusy(true);
    setError(null);
    try {
      await applySuggestion(suggestionId, null, { provider });
      onChanged?.();
    } catch (nextError) {
      setError(nextError);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="border-b border-border p-3 last:border-b-0">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="flex flex-wrap items-center gap-2 text-sm"><span className="font-semibold text-fg">{note.authorUsername || `Author ${note.authorId || 'unknown'}`}</span><span className="text-xs text-muted">{new Date(note.createdAt).toLocaleString()}</span>{position ? <span className="font-mono text-[10px] text-accent-ink">{position.newPath || position.oldPath}{position.newLine || position.oldLine ? `:${position.newLine || position.oldLine}` : ''}</span> : null}{note.state === 'draft' ? <span className="font-mono text-[10px] uppercase text-accent-ink">Draft</span> : null}{acceptance ? <Badge variant={acceptance.variant}>{acceptance.label}</Badge> : null}{onHide ? <Button className="!h-5 !px-1.5" size="xs" variant="ghost" title="Hide comment" aria-label="Hide comment" onClick={onHide}><EyeOff className="h-3.5 w-3.5" /></Button> : null}{acceptance?.stale ? <Badge variant="warning" title={`Assessed at ${note.assessedSha}`}>Stale · {note.assessedSha.slice(0, 8)}</Badge> : null}{acceptance?.unverified ? <Badge variant="warning" title="This legacy assessment did not record a commit SHA">SHA unknown</Badge> : null}</p>
        {note.state === 'draft' ? <div className="flex gap-1"><Button size="xs" variant="ghost" onClick={() => setEditing((value) => !value)}>{editing ? 'Cancel' : 'Edit'}</Button><Button size="xs" variant="danger" disabled={busy} onClick={discard}>Discard</Button></div> : null}
      </div>
      {editing ? <><textarea className="mt-2 min-h-20 w-full rounded-none border border-border-2 bg-surface-2 p-2 font-mono text-sm text-fg focus:border-accent focus:outline-none" value={body} onChange={(event) => setBody(event.target.value)} disabled={busy} /><Button className="mt-2" size="xs" disabled={!body.trim() || busy} onClick={save}>{busy ? 'Saving…' : 'Save draft'}</Button></> : commentBody ? <Markdown className="mt-2" collapseCodeBlocks>{commentBody}</Markdown> : null}
      {acceptance ? <details className="group mt-3 border-l-2 border-accent bg-[var(--ink-wash)]"><summary className="flex cursor-pointer list-none items-center gap-2 px-3 py-2 font-mono text-[11px] font-semibold uppercase tracking-wide text-accent-ink"><ChevronRight className="h-3.5 w-3.5 transition-transform group-open:rotate-90" /><span>AI assessment details</span></summary><div className="border-t border-border px-3 py-2 text-sm text-muted"><p>{note.assessmentReasoning || 'No explanation was provided.'}</p><p className="mt-2 font-mono text-[10px] uppercase">Assessed commit · {note.assessedSha?.slice(0, 8) || 'unknown'}</p></div></details> : null}
      {note.suggestions.map((suggestion) => <details key={suggestion.id} className="group mt-3 border border-border bg-surface"><summary className="flex cursor-pointer list-none items-center gap-2 px-3 py-2 text-xs font-semibold text-fg hover:bg-[var(--ink-wash)]"><ChevronRight className="h-3.5 w-3.5 transition-transform group-open:rotate-90" /><span>Suggested change</span><span className="ml-auto font-mono text-[10px] font-normal uppercase text-muted">Lines {suggestion.fromLine}–{suggestion.toLine}</span>{suggestion.applied ? <Badge variant="success">Applied</Badge> : null}</summary><div className="border-t border-border p-3"><div className="grid gap-2 text-xs sm:grid-cols-2"><pre className="overflow-auto whitespace-pre-wrap bg-[color-mix(in_oklch,var(--bad)_8%,transparent)] p-2 text-bad">{suggestion.fromContent}</pre><pre className="overflow-auto whitespace-pre-wrap bg-[color-mix(in_oklch,var(--ok)_8%,transparent)] p-2 text-ok">{suggestion.toContent}</pre></div>{suggestion.applied ? null : <div className="mt-2 flex justify-end"><Button size="xs" disabled={!suggestion.applicable || busy} onClick={() => apply(suggestion.id)}>Apply suggestion</Button></div>}</div></details>)}
      {error ? <ErrorState className="mt-2" title="Could not update draft" error={error} /> : null}
    </div>
  );
}

export function DiscussionThread({ thread, provider = 'GITLAB', projectId, changeNumber, headSha = null, enabled = false, collapsible = false, showPosition = false, onCollapsedChange, onChanged, onNavigate, className = '', ...props }) {
  const [resolutionBusy, setResolutionBusy] = useState(false);
  const [error, setError] = useState(null);

  async function toggleResolution() {
    setResolutionBusy(true);
    setError(null);
    try {
      await setDiscussionResolution(thread.discussionId, { provider, projectId: Number(projectId), mrIid: Number(changeNumber), resolved: !thread.resolved }, { provider });
      onChanged?.();
    } catch (nextError) {
      setError(nextError);
    } finally {
      setResolutionBusy(false);
    }
  }

  return (
    <article className={`border border-border bg-surface-2 ${className}`} {...props}>
      {onNavigate && thread.position ? (
        <button type="button" className="block w-full border-b border-border bg-surface px-3 py-1 text-left font-mono text-[11px] text-accent-ink hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" onClick={() => onNavigate(thread.discussionId || thread.id)}>Go to comment position ↗</button>
      ) : null}
      {thread.notes.map((note, index) => <DiscussionNote key={note.id} note={note} provider={provider} projectId={projectId} changeNumber={changeNumber} headSha={headSha} position={index === 0 && showPosition ? thread.position : null} onHide={index === 0 && collapsible ? onCollapsedChange : null} onChanged={onChanged} />)}
      {!thread.individualNote && thread.discussionId ? <DiscussionComposer compact className="border-0 border-t" provider={provider} projectId={projectId} changeNumber={changeNumber} discussionId={thread.discussionId} label="Reply" enabled={enabled} resolved={thread.resolved} resolutionBusy={resolutionBusy} onResolve={thread.resolvable ? toggleResolution : null} onChanged={onChanged} onRefresh={onChanged} /> : null}
      {error ? <ErrorState className="m-3" title="Could not update discussion" error={error} /> : null}
    </article>
  );
}
