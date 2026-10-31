'use client';

import { Button } from './Button';

export function ErrorState({ title = 'Something went wrong', error, onRetry, className = '', ...props }) {
  return (
    <div className={`border border-bad bg-surface px-5 py-6 ${className}`} role="alert" {...props}>
      <h2 className="font-display text-lg font-bold text-fg">{title}</h2>
      <p className="mt-2 text-sm text-muted">{error?.message || 'The request could not be completed.'}</p>
      {onRetry ? <Button className="mt-4" variant="secondary" size="sm" onClick={onRetry}>Retry</Button> : null}
    </div>
  );
}
