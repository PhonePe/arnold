'use client';

import React from 'react';

const dotColors = {
  success: 'bg-status-success',
  warning: 'bg-tertiary animate-pulse',
  error:   'bg-error',
  info:    'bg-status-info',
  neutral: 'bg-status-neutral',
};

const textColors = {
  success: 'text-status-success',
  warning: 'text-tertiary',
  error:   'text-error',
  info:    'text-status-info',
  neutral: 'text-status-neutral',
};

export function StatusIndicator({
  status = 'neutral',
  label,
  showDot = true,
  className = '',
  ...props
}) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider font-body ${textColors[status] || textColors.neutral} ${className}`}
      {...props}
    >
      {showDot && (
        <span className={`inline-block w-2 h-2 rounded-full ${dotColors[status] || dotColors.neutral}`} />
      )}
      {label}
    </span>
  );
}
