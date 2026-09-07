'use client';

import { useState } from 'react';
import { ChevronRight } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { ErrorState } from '@/components/ui/ErrorState';
import { Markdown } from '@/components/ui/Markdown';
import { DiscussionComposer } from '@/components/composite/DiscussionComposer';
import { applySuggestion, deleteDraftDiscussion, setDiscussionResolution, updateDraftDiscussion } from '@/lib/api';
import { withoutSuggestionBlocks } from '@/lib/markdown-details.mjs';

function DiscussionNote({ note, projectId, changeNumber, onChanged }) {
  const [editing, setEditing] = useState(false);
  const [body, setBody] = useState(note.body);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const commentBody = note.suggestions.length ? withoutSuggestionBlocks(note.body) : note.body;

  async function save() {
    setBusy(true);
    setError(null);
    try {
      await updateDraftDiscussion(note.draftId, { projectId: Number(projectId), mrIid: Number(changeNumber), body: body.trim() });
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
      await deleteDraftDiscussion(note.draftId, projectId, changeNumber);
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
      await applySuggestion(suggestionId);
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
        <p className="text-sm"><span className="font-semibold text-fg">{note.authorUsername || `Author ${note.authorId || 'unknown'}`}</span><span className="ml-2 text-xs text-muted">{new Date(note.createdAt).toLocaleString()}</span>{note.state === 'draft' ? <span className="ml-2 font-mono text-[10px] uppercase text-accent-ink">Draft</span> : null}</p>
        {note.state === 'draft' ? <div className="flex gap-1"><Button size="xs" variant="ghost" onClick={() => setEditing((value) => !value)}>{editing ? 'Cancel' : 'Edit'}</Button><Button size="xs" variant="danger" disabled={busy} onClick={discard}>Discard</Button></div> : null}
      </div>
      {editing ? <><textarea className="mt-2 min-h-20 w-full rounded-none border border-border-2 bg-surface-2 p-2 font-mono text-sm text-fg focus:border-accent focus:outline-none" value={body} onChange={(event) => setBody(event.target.value)} disabled={busy} /><Button className="mt-2" size="xs" disabled={!body.trim() || busy} onClick={save}>{busy ? 'Saving…' : 'Save draft'}</Button></> : commentBody ? <Markdown className="mt-2" collapseCodeBlocks>{commentBody}</Markdown> : null}
      {note.suggestions.map((suggestion) => <details key={suggestion.id} className="group mt-3 border border-border bg-surface"><summary className="flex cursor-pointer list-none items-center gap-2 px-3 py-2 text-xs font-semibold text-fg hover:bg-[var(--ink-wash)]"><ChevronRight className="h-3.5 w-3.5 transition-transform group-open:rotate-90" /><span>Suggested change</span><span className="ml-auto font-mono text-[10px] font-normal uppercase text-muted">Lines {suggestion.fromLine}–{suggestion.toLine}</span>{suggestion.applied ? <Badge variant="success">Applied</Badge> : null}</summary><div className="border-t border-border p-3"><div className="grid gap-2 text-xs sm:grid-cols-2"><pre className="overflow-auto whitespace-pre-wrap bg-[color-mix(in_oklch,var(--bad)_8%,transparent)] p-2 text-bad">{suggestion.fromContent}</pre><pre className="overflow-auto whitespace-pre-wrap bg-[color-mix(in_oklch,var(--ok)_8%,transparent)] p-2 text-ok">{suggestion.toContent}</pre></div>{suggestion.applied ? null : <div className="mt-2 flex justify-end"><Button size="xs" disabled={!suggestion.applicable || busy} onClick={() => apply(suggestion.id)}>Apply suggestion</Button></div>}</div></details>)}
      {error ? <ErrorState className="mt-2" title="Could not update draft" error={error} /> : null}
    </div>
  );
}

export function DiscussionThread({ thread, provider = 'GITLAB', projectId, changeNumber, enabled = false, collapsible = false, showPosition = false, onCollapsedChange, onChanged, className = '', ...props }) {
  const [resolutionBusy, setResolutionBusy] = useState(false);
  const [error, setError] = useState(null);

  async function toggleResolution() {
    setResolutionBusy(true);
    setError(null);
    try {
      await setDiscussionResolution(thread.discussionId, { projectId: Number(projectId), mrIid: Number(changeNumber), resolved: !thread.resolved });
      onChanged?.();
    } catch (nextError) {
      setError(nextError);
    } finally {
      setResolutionBusy(false);
    }
  }

  return (
    <article className={`border border-border bg-surface-2 ${className}`} {...props}>
      {!thread.individualNote ? <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border bg-surface px-3 py-2">
        <div><p className="font-mono text-[10px] uppercase text-muted">{thread.individualNote ? 'Note' : `${thread.notes.length} ${thread.notes.length === 1 ? 'comment' : 'comments'}`} · {thread.individualNote ? thread.notes[0].state : thread.resolved ? 'resolved' : 'open'}</p>{showPosition && thread.position ? <p className="mt-1 break-all font-mono text-[10px] text-accent-ink">{thread.position.newPath || thread.position.oldPath}{thread.position.newLine || thread.position.oldLine ? `:${thread.position.newLine || thread.position.oldLine}` : ''}</p> : null}</div>
        <div className="flex gap-1">{collapsible ? <Button size="xs" variant="ghost" onClick={() => onCollapsedChange?.()}>Hide comment</Button> : null}{!thread.individualNote && thread.discussionId && thread.resolvable ? <Button size="xs" variant="secondary" disabled={!enabled || resolutionBusy} onClick={toggleResolution}>{resolutionBusy ? 'Updating…' : thread.resolved ? 'Reopen' : 'Resolve'}</Button> : null}</div>
      </div> : null}
      {thread.notes.map((note) => <DiscussionNote key={note.id} note={note} projectId={projectId} changeNumber={changeNumber} onChanged={onChanged} />)}
      {!thread.individualNote && thread.discussionId ? <DiscussionComposer className="border-0 border-t" provider={provider} projectId={projectId} changeNumber={changeNumber} discussionId={thread.discussionId} label="Reply" enabled={enabled} onChanged={onChanged} onRefresh={onChanged} /> : null}
      {error ? <ErrorState className="m-3" title="Could not update discussion" error={error} /> : null}
    </article>
  );
}
