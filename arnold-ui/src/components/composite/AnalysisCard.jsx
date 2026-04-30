'use client';

import React from 'react';
import { StatusIndicator } from '@/components/ui/StatusIndicator';

const accentColors = {
  'in-progress': 'border-l-status-info',
  complete: 'border-l-status-success',
  'critical-alert': 'border-l-error',
  error: 'border-l-error',
  default: 'border-l-primary',
};

export function AnalysisCard({
  analysisId,
  title,
  status = 'in-progress',
  onClick,
  className = '',
  ...props
}) {
  const statusMap = {
    'in-progress': { status: 'info', label: 'IN PROGRESS' },
    complete: { status: 'success', label: 'COMPLETE' },
    'critical-alert': { status: 'error', label: 'CRITICAL ALERT' },
  };

  const accentClass = accentColors[status] || accentColors.default;
  const statusConfig = statusMap[status] || statusMap['in-progress'];

  return (
    <div
      className={`rounded-lg bg-surface-container overflow-hidden hover:bg-surface-container-high transition-colors cursor-pointer ${className}`}
      onClick={onClick}
      role="button"
      tabIndex={0}
      {...props}
    >
      <div className={`border-l-4 ${accentClass} p-4`}>
        <div className="flex items-center justify-between">
          <div>
            {analysisId && (
              <span className="text-[11px] font-semibold uppercase tracking-widest text-on-surface-faint font-body">
                {analysisId}
              </span>
            )}
            <p className="text-sm font-medium text-on-surface font-body">{title}</p>
          </div>
          <StatusIndicator
            status={statusConfig.status}
            label={statusConfig.label}
            showDot={false}
          />
        </div>
      </div>
    </div>
  );
}
