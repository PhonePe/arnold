'use client';

import React from 'react';
import { Badge } from '@/components/ui/Badge';

export function RuleCard({
  category,
  variant = 'default',
  description,
  action,
  actionLabel = 'OVERRIDE',
  onAction,
  footnote,
  className = '',
  ...props
}) {
  return (
    <div
      className={`rounded-sm bg-surface-container-high p-4 space-y-3 ${className}`}
      {...props}
    >
      <div className="flex items-center justify-between">
        <Badge variant={variant}>{category}</Badge>
        {actionLabel && (
          <button
            onClick={onAction}
            className="text-[11px] font-medium uppercase tracking-wider font-body text-on-surface-faint hover:text-on-surface transition-colors"
          >
            {actionLabel}
          </button>
        )}
      </div>

      <p className="text-sm text-on-surface font-body">{description}</p>

      {footnote && (
        <p className="text-xs text-on-surface-faint font-body">{footnote}</p>
      )}
    </div>
  );
}
