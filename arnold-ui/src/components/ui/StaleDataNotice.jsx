'use client';

import { isStale } from '@/lib/contracts';

export function StaleDataNotice({ syncedAt, className = '', ...props }) {
  if (!isStale(syncedAt)) return null;
  return (
    <p className={`border-l-[3px] border-l-tertiary bg-[var(--ink-wash)] px-3 py-2 font-mono text-[11px] text-muted ${className}`} {...props}>
      Stale data: last synchronized {new Date(syncedAt).toLocaleString()}.
    </p>
  );
}
