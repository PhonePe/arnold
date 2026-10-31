'use client';

import React from 'react';

const deltaTones = {
  up: 'text-ok',
  down: 'text-bad',
  warn: 'text-tertiary-ink',
  neutral: 'text-muted',
};

// Lattice stat card (.card.stat): a large Space Grotesk figure, a quiet label,
// and an optional mono delta line. Hairline border, sharp corners.
export function StatCard({
  label,
  value,
  accent = false,
  delta,
  deltaTone = 'neutral',
  icon,
  className = '',
  children,
  ...props
}) {
  return (
    <div
      className={`rounded-none border border-border bg-surface px-5 py-[18px] ${className}`}
      {...props}
    >
      <div className="flex items-start justify-between">
        <div>
          {value != null && (
            <div
              className={`font-display text-[40px] font-bold leading-none tracking-[-0.03em] ${
                accent ? 'text-accent' : 'text-fg'
              }`}
            >
              {value}
            </div>
          )}
          {label && <div className="mt-[7px] text-[13px] text-muted">{label}</div>}
          {delta && (
            <div className={`mt-1.5 font-mono text-[11px] ${deltaTones[deltaTone] || deltaTones.neutral}`}>
              {delta}
            </div>
          )}
        </div>
        {icon && <span className="text-faint">{icon}</span>}
      </div>
      {children}
    </div>
  );
}
