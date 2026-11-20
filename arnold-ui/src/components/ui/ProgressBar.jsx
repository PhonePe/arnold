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

const colorMap = {
  primary: 'bg-accent',
  accent: 'bg-accent',
  tertiary: 'bg-tertiary',
  success: 'bg-ok',
  info: 'bg-[var(--sev-maint)]',
  error: 'bg-bad',
};

// Meter: ink-wash track, accent fill. Pill ends read fine for meters.
export function ProgressBar({
  value = 0,
  max = 100,
  color = 'primary',
  className = '',
  ...props
}) {
  const pct = Math.min(100, Math.max(0, (value / max) * 100));

  return (
    <div
      className={`h-1.5 w-full overflow-hidden rounded-full bg-[var(--ink-wash)] ${className}`}
      role="progressbar"
      aria-valuenow={value}
      aria-valuemin={0}
      aria-valuemax={max}
      {...props}
    >
      <div
        className={`h-full rounded-full transition-all duration-300 ${colorMap[color] || colorMap.primary}`}
        style={{ width: `${pct}%` }}
      />
    </div>
  );
}
