'use client';

import React from 'react';
import { StatusIndicator } from '@/components/ui/StatusIndicator';

export function StatusBar({
  items = [],
  version,
  className = '',
  ...props
}) {
  return (
    <footer
      className={`flex items-center justify-between h-10 px-6 bg-surface-container-low text-xs ${className}`}
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
        <span className="text-on-surface-faint font-body">{version}</span>
      )}
    </footer>
  );
}
