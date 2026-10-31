'use client';

import React from 'react';

// Lattice inputs: hairline-bordered on the highest surface, accent border +
// soft ring on focus. Sharp corners.
export function Input({ className = '', ...props }) {
  return (
    <input
      className={
        'flex h-9 w-full rounded-none border border-border-2 bg-surface-2 px-3 py-2 text-[13px] text-fg ' +
        'placeholder:text-faint ' +
        'focus-visible:outline-none focus-visible:border-accent focus-visible:ring-[3px] focus-visible:ring-[var(--accent-soft)] ' +
        'disabled:cursor-not-allowed disabled:opacity-50 ' +
        'transition-colors ' +
        className
      }
      {...props}
    />
  );
}
