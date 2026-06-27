'use client';

import React from 'react';

// Lattice tabs. Two variants:
//   - `underline` (default): mono-ish text with a sharp accent rail under the
//     active tab.
//   - `segment`: a boxed segmented control (ink-wash track, raised active chip).
export function Tabs({
  items = [],
  activeKey,
  onTabChange,
  variant = 'underline',
  className = '',
  ...props
}) {
  if (variant === 'segment') {
    return (
      <div
        className={`inline-flex gap-0.5 border border-border bg-[var(--ink-wash)] p-[3px] ${className}`}
        {...props}
      >
        {items.map((item) => {
          const isActive = item.key === activeKey;
          return (
            <button
              key={item.key}
              onClick={() => onTabChange?.(item.key)}
              className={`rounded-none px-3 py-1.5 font-mono text-[12px] font-semibold tracking-[0.02em] transition-colors ${
                isActive
                  ? 'bg-surface-2 text-fg shadow-[0_1px_2px_rgba(0,0,0,0.05)]'
                  : 'text-muted hover:text-fg'
              }`}
            >
              {item.label}
            </button>
          );
        })}
      </div>
    );
  }

  return (
    <nav className={`flex items-center gap-6 ${className}`} {...props}>
      {items.map((item) => {
        const isActive = item.key === activeKey;
        return (
          <button
            key={item.key}
            onClick={() => onTabChange?.(item.key)}
            className={`relative pb-2 text-sm font-semibold transition-colors ${
              isActive ? 'text-fg' : 'text-muted hover:text-fg'
            }`}
          >
            {item.label}
            {isActive && (
              <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-accent" />
            )}
          </button>
        );
      })}
    </nav>
  );
}
