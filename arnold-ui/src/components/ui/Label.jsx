'use client';

import React from 'react';

// Lattice field label: mono, uppercase, small with tracking.
export function Label({ className = '', children, ...props }) {
  return (
    <label
      className={`font-mono text-[11px] uppercase leading-none tracking-[0.06em] text-muted peer-disabled:cursor-not-allowed peer-disabled:opacity-70 ${className}`}
      {...props}
    >
      {children}
    </label>
  );
}
