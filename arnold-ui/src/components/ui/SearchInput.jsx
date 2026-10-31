'use client';

import React from 'react';
import { Search } from 'lucide-react';

// Lattice search field: hairline border on raised surface, accent border on
// focus, mono-ish search affordance.
export function SearchInput({
  placeholder = 'Search...',
  className = '',
  ...props
}) {
  return (
    <div className={`relative ${className}`}>
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-faint" />
      <input
        type="text"
        placeholder={placeholder}
        className={
          'h-9 w-full rounded-none border border-border bg-surface pl-9 pr-3 text-[13px] text-fg ' +
          'placeholder:text-faint ' +
          'focus-visible:outline-none focus-visible:border-accent focus-visible:ring-[3px] focus-visible:ring-[var(--accent-soft)] ' +
          'transition-colors'
        }
        {...props}
      />
    </div>
  );
}
