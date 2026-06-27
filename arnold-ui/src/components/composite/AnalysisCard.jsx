'use client';

import React from 'react';
import { StatusIndicator } from '@/components/ui/StatusIndicator';

const accentColors = {
  'in-progress': 'border-l-[var(--sev-maint)]',
  complete: 'border-l-ok',
  'critical-alert': 'border-l-bad',
  error: 'border-l-bad',
  default: 'border-l-accent',
};

const statusMap = {
  'in-progress': { status: 'warning', label: 'In progress' },
  complete: { status: 'success', label: 'Complete' },
  'critical-alert': { status: 'error', label: 'Critical' },
};

// Lattice analysis row: hairline border + a colored left accent bar, mono id,
// title, and a status indicator. Sharp corners.
export function AnalysisCard({
  analysisId,
  title,
  status = 'in-progress',
  onClick,
  className = '',
  ...props
}) {
  const accentClass = accentColors[status] || accentColors.default;
  const statusConfig = statusMap[status] || statusMap['in-progress'];

  return (
    <div
      className={`rounded-none border border-border border-l-[3px] ${accentClass} bg-surface px-4 py-3 transition-colors hover:border-border-2 ${
        onClick ? 'cursor-pointer' : ''
      } ${className}`}
      onClick={onClick}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      {...props}
    >
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          {analysisId && (
            <span className="font-mono text-[11px] uppercase tracking-[0.1em] text-faint">
              {analysisId}
            </span>
          )}
          <p className="truncate text-[13.5px] font-medium text-fg">{title}</p>
        </div>
        <StatusIndicator status={statusConfig.status} label={statusConfig.label} />
      </div>
    </div>
  );
}
