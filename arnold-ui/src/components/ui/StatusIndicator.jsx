'use client';

import React from 'react';

const dotColors = {
  success: 'bg-ok',
  warning: 'bg-tertiary animate-pulse',
  error:   'bg-bad',
  info:    'bg-[var(--sev-maint)]',
  accent:  'bg-accent',
  neutral: 'bg-muted',
};

const textColors = {
  success: 'text-ok',
  warning: 'text-tertiary-ink',
  error:   'text-bad',
  info:    'text-[var(--sev-maint)]',
  accent:  'text-accent-ink',
  neutral: 'text-muted',
};

// Lattice status indicator: mono, uppercase, tracked, with a state dot.
export function StatusIndicator({
  status = 'neutral',
  label,
  showDot = true,
  className = '',
  ...props
}) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 font-mono text-[11px] uppercase tracking-wide ${textColors[status] || textColors.neutral} ${className}`}
      {...props}
    >
      {showDot && (
        <span className={`inline-block h-2 w-2 rounded-full ${dotColors[status] || dotColors.neutral}`} />
      )}
      {label}
    </span>
  );
}
