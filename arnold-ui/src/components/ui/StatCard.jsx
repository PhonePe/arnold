'use client';

import React from 'react';

export function StatCard({
  label,
  value,
  icon,
  className = '',
  children,
  ...props
}) {
  return (
    <div
      className={`rounded-lg bg-surface-container p-5 ${className}`}
      {...props}
    >
      <div className="flex items-start justify-between">
        <div className="space-y-1">
          {label && (
            <span className="text-[11px] font-semibold uppercase tracking-widest text-on-surface-muted font-body">
              {label}
            </span>
          )}
          {value && (
            <p className="text-2xl font-bold font-headline text-on-surface">{value}</p>
          )}
        </div>
        {icon && (
          <span className="text-on-surface-faint">{icon}</span>
        )}
      </div>
      {children}
    </div>
  );
}
