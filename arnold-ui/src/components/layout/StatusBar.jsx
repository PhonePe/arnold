'use client';

import React from 'react';
import { StatusIndicator } from '@/components/ui/StatusIndicator';

// Lattice status bar: a quiet footer rail with a hairline top border, mono
// status indicators on the left and a version tag on the right.
export function StatusBar({
  items = [],
  version,
  className = '',
  ...props
}) {
  return (
    <footer
      className={`flex h-10 items-center justify-between border-t border-border bg-surface px-6 text-xs ${className}`}
      {...props}
    >
      <div className="flex items-center gap-4">
        {items.map((item, i) => (
          <StatusIndicator
            key={i}
            status={item.status || 'neutral'}
            label={item.label}
          />
        ))}
      </div>
      {version && (
        <span className="font-mono text-[11px] text-faint">{version}</span>
      )}
    </footer>
  );
}
