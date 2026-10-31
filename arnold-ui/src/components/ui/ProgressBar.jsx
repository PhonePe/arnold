'use client';

import React from 'react';

const colorMap = {
  primary: 'bg-accent',
  accent: 'bg-accent',
  tertiary: 'bg-tertiary',
  success: 'bg-ok',
  info: 'bg-[var(--sev-maint)]',
  error: 'bg-bad',
};

// Lattice meter: ink-wash track, accent fill. Pill ends read fine for meters.
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
