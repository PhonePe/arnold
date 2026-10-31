'use client';

import React from 'react';

// Lattice badges: mono font, sharp corners, soft tinted backgrounds drawn
// from the severity taxonomy. Vermilion signal stays dominant.
const presets = {
  security:    'bg-[color-mix(in_oklch,var(--sev-security)_14%,transparent)] text-[var(--sev-security)]',
  style:       'bg-[var(--accent-soft)] text-accent-ink',
  logic:       'bg-[color-mix(in_oklch,var(--sev-maint)_14%,transparent)] text-[var(--sev-maint)]',
  runtime:     'bg-[var(--accent-soft)] text-accent-ink',
  coreUi:      'bg-[color-mix(in_oklch,var(--ok)_14%,transparent)] text-ok',
  performance: 'bg-[color-mix(in_oklch,var(--sev-perf)_18%,transparent)] text-[var(--sev-perf)]',
  learnedRule: 'bg-[var(--tertiary-soft)] text-tertiary-ink',
  legacy:      'bg-[var(--ink-wash)] text-muted',
  nit:         'bg-[var(--ink-wash)] text-muted',
  info:        'bg-[color-mix(in_oklch,var(--sev-maint)_14%,transparent)] text-[var(--sev-maint)]',
  success:     'bg-[color-mix(in_oklch,var(--ok)_14%,transparent)] text-ok',
  warning:     'bg-[var(--tertiary-soft)] text-tertiary-ink',
  error:       'bg-[color-mix(in_oklch,var(--bad)_12%,transparent)] text-bad',
  critical:    'bg-[var(--accent-soft)] text-accent-ink',
  default:     'bg-[var(--ink-wash)] text-muted',
};

export function Badge({
  variant = 'default',
  className = '',
  children,
  ...props
}) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-none px-2 py-0.5 font-mono text-[11px] font-semibold uppercase tracking-wide ${presets[variant] || presets.default} ${className}`}
      {...props}
    >
      {children}
    </span>
  );
}
