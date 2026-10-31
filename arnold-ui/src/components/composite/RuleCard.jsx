'use client';

import React from 'react';
import { Badge } from '@/components/ui/Badge';

// Lattice rule card: highest surface with a hairline border, a category badge,
// a mono override action, and the rule body. Sharp corners.
export function RuleCard({
  category,
  variant = 'default',
  description,
  action,
  actionLabel = 'Override',
  onAction,
  footnote,
  className = '',
  ...props
}) {
  return (
    <div
      className={`space-y-3 rounded-none border border-border bg-surface-2 p-4 ${className}`}
      {...props}
    >
      <div className="flex items-center justify-between">
        <Badge variant={variant}>{category}</Badge>
        {actionLabel && (
          <button
            onClick={onAction}
            className="font-mono text-[11px] uppercase tracking-[0.06em] text-faint transition-colors hover:text-accent"
          >
            {actionLabel}
          </button>
        )}
      </div>

      <p className="text-sm text-fg">{description}</p>

      {footnote && <p className="font-mono text-[11px] text-faint">{footnote}</p>}
    </div>
  );
}
