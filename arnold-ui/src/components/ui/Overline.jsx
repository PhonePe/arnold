'use client';

import React from 'react';

// Lattice eyebrow/overline: IBM Plex Mono, uppercase, tracked.
// Default tone is the vermilion accent (the signature eyebrow);
// `tone="muted"` / `tone="faint"` for quieter labels.
const tones = {
  accent: 'text-accent',
  muted: 'text-muted',
  faint: 'text-faint',
};

export function Overline({ className = '', children, tone = 'accent', ...props }) {
  return (
    <span
      className={`font-mono text-[11px] uppercase tracking-[0.12em] ${tones[tone] || tones.accent} ${className}`}
      {...props}
    >
      {children}
    </span>
  );
}
