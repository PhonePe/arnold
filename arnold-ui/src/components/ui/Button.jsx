'use client';

import React from 'react';

const variants = {
  primary:
    'cta-gradient text-on-primary font-semibold',
  secondary:
    'bg-secondary-container text-secondary hover:bg-surface-container-highest',
  inverted:
    'bg-on-surface text-surface hover:opacity-90',
  outline:
    'ghost-border bg-transparent text-on-surface hover:bg-surface-container-high',
  ghost:
    'bg-transparent text-on-surface-muted hover:bg-surface-container hover:text-on-surface',
  danger:
    'bg-error-container text-error hover:opacity-90',
  link:
    'bg-transparent text-primary underline-offset-4 hover:underline p-0 h-auto',
};

const sizes = {
  sm: 'h-8 px-3 text-xs gap-1.5',
  md: 'h-10 px-4 text-sm gap-2',
  lg: 'h-11 px-6 text-sm gap-2',
};

export function Button({
  variant = 'primary',
  size = 'md',
  className = '',
  children,
  ...props
}) {
  const base =
    'inline-flex items-center justify-center whitespace-nowrap rounded-sm font-medium transition-colors ' +
    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-surface ' +
    'disabled:pointer-events-none disabled:opacity-50';

  return (
    <button
      className={`${base} ${sizes[size] || sizes.md} ${variants[variant] || variants.primary} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}
