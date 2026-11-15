'use client';

/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
