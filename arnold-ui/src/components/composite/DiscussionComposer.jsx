'use client';

import { useRef, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { ErrorState } from '@/components/ui/ErrorState';
import { createDiscussion } from '@/lib/api';

export function DiscussionComposer({ provider, projectId, changeNumber, enabled = false, onPublished, onRefresh, className = '', ...props }) {
  const [body, setBody] = useState('');
  const [status, setStatus] = useState('idle');
  const [error, setError] = useState(null);
  const operationId = useRef(null);

  function edit(nextBody) {
    setBody(nextBody);
    if (status === 'unknown' || status === 'failed') {
      operationId.current = null;
      setStatus('edited-after-failure');
    }
  }

  function discard() {
    setBody('');
    setError(null);
    setStatus('idle');
    operationId.current = null;
  }

  async function submit(event) {
    event.preventDefault();
    if (!body.trim() || !enabled || status === 'unknown') return;
    operationId.current ||= crypto.randomUUID();
    setStatus('submitting');
    setError(null);
    try {
      const discussion = await createDiscussion({ provider, projectId: Number(projectId), mrIid: Number(changeNumber), body: body.trim(), clientOperationId: operationId.current });
      if (discussion.state === 'unknown') {
        setStatus('unknown');
      } else if (discussion.state === 'published') {
        discard();
        onPublished?.(discussion);
      } else {
        setStatus(discussion.state === 'pending' ? 'pending' : 'unknown');
      }
    } catch (nextError) {
      setError(nextError);
      setStatus(nextError.errorCode === 4000 || nextError.status >= 500 ? 'unknown' : 'failed');
    }
  }

  return (
    <form onSubmit={submit} className={`border border-border bg-surface p-4 ${className}`} {...props}>
      <label htmlFor="discussion-body" className="font-mono text-[11px] uppercase tracking-[0.06em] text-muted">New top-level comment</label>
      <textarea id="discussion-body" className="mt-2 min-h-28 w-full border border-border-2 bg-surface-2 p-3 text-sm text-fg focus:border-accent focus:outline-none" value={body} onChange={(event) => edit(event.target.value)} disabled={!enabled || status === 'submitting' || status === 'pending'} />
      {!enabled ? <p className="mt-2 text-sm text-muted">Publishing comments is not configured.</p> : null}
      {status === 'edited-after-failure' ? <p className="mt-2 text-sm text-tertiary-ink">This edit creates a new operation. Duplicate provider publication is possible.</p> : null}
      {status === 'unknown' ? <p role="alert" className="mt-2 text-sm text-tertiary-ink">Delivery status unknown. Do not resubmit this unchanged draft.</p> : null}
      {error && status === 'failed' ? <ErrorState className="mt-3" title="Could not publish comment" error={error} /> : null}
      <div className="mt-3 flex flex-wrap gap-2">
        <Button type="submit" size="sm" disabled={!enabled || !body.trim() || ['submitting', 'pending', 'unknown'].includes(status)} aria-busy={status === 'submitting'}>{status === 'submitting' || status === 'pending' ? 'Publishing…' : 'Publish comment'}</Button>
        {status === 'unknown' ? <Button type="button" size="sm" variant="secondary" onClick={onRefresh}>Refresh discussions</Button> : null}
        {body ? <Button type="button" size="sm" variant="ghost" onClick={discard}>Discard local draft</Button> : null}
      </div>
    </form>
  );
}
