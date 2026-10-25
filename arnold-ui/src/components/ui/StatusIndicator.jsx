'use client';

/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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
