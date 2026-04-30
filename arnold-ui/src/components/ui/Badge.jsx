'use client';

import React from 'react';

const presets = {
  security:    'bg-status-success/15 text-status-success',
  style:       'bg-primary/15 text-primary',
  logic:       'bg-status-info/15 text-status-info',
  runtime:     'bg-primary/15 text-primary',
  coreUi:      'bg-status-success/15 text-status-success',
  learnedRule: 'glass text-tertiary',
  legacy:      'bg-surface-container-high text-on-surface-muted',
  nit:         'bg-surface-container-high text-on-surface-muted',
  info:        'bg-status-info/15 text-status-info',
  success:     'bg-status-success/15 text-status-success',
  warning:     'glass text-tertiary',
  error:       'bg-error-container/40 text-error',
  default:     'bg-surface-container-high text-on-surface-muted',
};

export function Badge({
  variant = 'default',
  className = '',
  children,
  ...props
}) {
  return (
    <span
      className={`inline-flex items-center rounded-sm px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wider ${presets[variant] || presets.default} ${className}`}
      {...props}
    >
      {children}
    </span>
  );
}
