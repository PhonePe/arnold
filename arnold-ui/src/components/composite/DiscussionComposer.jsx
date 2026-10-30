'use client';

import { useId, useRef, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { ErrorState } from '@/components/ui/ErrorState';
import { createDiscussion, createDraftDiscussion } from '@/lib/api';

function createOperationId() {
  return globalThis.crypto?.randomUUID?.()
    || `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

export function DiscussionComposer({
  provider,
  projectId,
  changeNumber,
  discussionId = null,
  position = null,
  suggestion = '',
  label = 'New top-level comment',
  compact = false,
  enabled = false,
  resolved = false,
  resolutionBusy = false,
  onResolve,
  onChanged,
  onRefresh,
  onCancel,
  className = '',
  ...props
}) {
  const [body, setBody] = useState('');
  const [compactOpen, setCompactOpen] = useState(false);
  const textareaId = useId();
  const [status, setStatus] = useState('idle');
  const [error, setError] = useState(null);
  const operationId = useRef(null);

  function edit(nextBody) {
    setBody(nextBody);
    if (status === 'unknown' || status === 'failed') {
      operationId.current = null;
      setStatus('idle');
    }
  }

  function discard() {
    setBody('');
    setCompactOpen(false);
    setError(null);
    setStatus('idle');
    operationId.current = null;
  }

  async function submit(mode) {
    if (!body.trim() || !enabled || status === 'unknown') return;
    setStatus(mode === 'draft' ? 'drafting' : 'publishing');
    setError(null);
    try {
      operationId.current ||= createOperationId();
      const request = {
        provider,
        projectId: Number(projectId),
        mrIid: Number(changeNumber),
        body: body.trim(),
        clientOperationId: operationId.current,
        discussionId,
        position,
      };
      const discussion = mode === 'draft'
        ? await createDraftDiscussion(request)
        : await createDiscussion(request);
      if (discussion.state === 'unknown') {
        setStatus('unknown');
        return;
      }
      discard();
      onChanged?.(discussion);
    } catch (nextError) {
      setError(nextError);
      setStatus(mode === 'immediate' && (nextError.errorCode === 4000 || nextError.status >= 500) ? 'unknown' : 'failed');
    }
  }

  const busy = status === 'drafting' || status === 'publishing';
  function insertSuggestion() {
    edit(`\`\`\`suggestion\n${suggestion}\n\`\`\``);
  }

  function handleKeyDown(event) {
    if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') {
      event.preventDefault();
      submit(compact || event.shiftKey ? 'immediate' : 'draft');
    }
  }

  if (compact && !compactOpen) {
    return (
      <form className={`flex items-center gap-2 border border-border bg-surface p-2 ${className}`} {...props}>
        <button type="button" className="h-8 flex-1 border border-border-2 bg-surface-2 px-2 text-left text-sm text-faint hover:border-accent disabled:cursor-not-allowed disabled:opacity-60" disabled={!enabled} onClick={() => setCompactOpen(true)}>Reply…</button>
        {onResolve ? <Button type="button" size="xs" variant="secondary" disabled={!enabled || resolutionBusy} onClick={onResolve}>{resolutionBusy ? 'Updating…' : resolved ? 'Reopen' : 'Resolve'}</Button> : null}
      </form>
    );
  }

  return (
    <form onSubmit={(event) => { event.preventDefault(); submit(compact ? 'immediate' : 'draft'); }} className={`border border-border bg-surface ${compact ? 'p-3' : 'p-4'} ${className}`} {...props}>
      {compact ? <label htmlFor={textareaId} className="sr-only">{label}</label> : <div className="flex flex-wrap items-center justify-between gap-2"><label htmlFor={textareaId} className="font-mono text-[11px] uppercase tracking-[0.06em] text-muted">{label}</label><span className="font-mono text-[10px] text-faint">Markdown supported</span></div>}
      {position?.lineRange ? <p className="mt-2 text-sm text-muted">{position.lineRange.side === 'old' ? 'Old' : 'New'} lines {position.lineRange.side === 'old' ? position.lineRange.startOldLine : position.lineRange.startNewLine}–{position.lineRange.side === 'old' ? position.lineRange.endOldLine : position.lineRange.endNewLine}</p> : position?.newLine != null || position?.oldLine != null ? <p className="mt-2 text-sm text-muted">{position.newLine != null ? `New line ${position.newLine}` : `Old line ${position.oldLine}`}</p> : null}
      <textarea id={textareaId} autoFocus={compact} className={`${compact ? 'min-h-14 p-2' : 'mt-2 min-h-24 p-3'} w-full rounded-none border border-border-2 bg-surface-2 font-mono text-sm text-fg focus:border-accent focus:outline-none`} placeholder={compact ? 'Reply…' : undefined} value={body} onChange={(event) => edit(event.target.value)} onKeyDown={handleKeyDown} disabled={!enabled || busy} />
      {!enabled ? <p className="mt-2 text-sm text-muted">Publishing comments is not configured.</p> : null}
      {status === 'unknown' ? <p role="alert" className="mt-2 text-sm text-tertiary-ink">Delivery status is unknown. Refresh GitLab discussions before retrying.</p> : null}
      {error && status === 'failed' ? <ErrorState className="mt-3" title="Could not save comment" error={error} /> : null}
      {compact ? <div className="mt-2 flex flex-wrap justify-end gap-2">
        {status === 'unknown' ? <Button type="button" size="xs" variant="secondary" onClick={onRefresh}>Refresh discussions</Button> : null}
        <Button type="button" size="xs" variant="ghost" disabled={busy} onClick={discard}>Cancel</Button>
        {onResolve ? <Button type="button" size="xs" variant="secondary" disabled={!enabled || resolutionBusy} onClick={onResolve}>{resolutionBusy ? 'Updating…' : resolved ? 'Reopen' : 'Resolve'}</Button> : null}
        <Button type="submit" size="xs" disabled={!enabled || !body.trim() || busy || status === 'unknown'} aria-busy={status === 'publishing'}>{status === 'publishing' ? 'Replying…' : 'Reply'}</Button>
      </div> : <div className="mt-3 flex flex-wrap gap-2">
        {suggestion ? <Button type="button" size="sm" variant="secondary" disabled={!enabled || busy} onClick={insertSuggestion}>Insert suggestion</Button> : null}
        <Button type="submit" size="sm" disabled={!enabled || !body.trim() || busy || status === 'unknown'} aria-busy={status === 'drafting'}>{status === 'drafting' ? 'Adding…' : 'Add to review'}</Button>
        <Button type="button" size="sm" variant="secondary" disabled={!enabled || !body.trim() || busy || status === 'unknown'} aria-busy={status === 'publishing'} onClick={() => submit('immediate')}>{status === 'publishing' ? 'Publishing…' : 'Comment now'}</Button>
        {status === 'unknown' ? <Button type="button" size="sm" variant="secondary" onClick={onRefresh}>Refresh discussions</Button> : null}
        {body ? <Button type="button" size="sm" variant="ghost" disabled={busy} onClick={discard}>Discard text</Button> : null}
        {onCancel ? <Button type="button" size="sm" variant="ghost" disabled={busy} onClick={onCancel}>Close</Button> : null}
      </div>}
      {compact ? null : <p className="mt-2 font-mono text-[10px] text-faint">⌘/Ctrl+Enter adds to review · Shift+⌘/Ctrl+Enter comments now</p>}
    </form>
  );
}
