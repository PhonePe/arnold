'use client';

import React from 'react';

export function Overline({ className = '', children, ...props }) {
  return (
    <span
      className={`text-[11px] font-semibold uppercase tracking-widest text-on-surface-muted font-body ${className}`}
      {...props}
    >
      {children}
    </span>
  );
}
