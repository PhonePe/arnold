'use client';

import React from 'react';

// Lattice cards: hairline border, sharp corners, raised surface.
export function Card({ className = '', children, ...props }) {
  return (
    <div
      className={`rounded-none border border-border bg-surface text-fg ${className}`}
      {...props}
    >
      {children}
    </div>
  );
}

export function CardHeader({ className = '', children, ...props }) {
  return (
    <div className={`p-5 pb-2 ${className}`} {...props}>
      {children}
    </div>
  );
}

export function CardTitle({ className = '', children, ...props }) {
  return (
    <h3 className={`font-display text-lg font-bold tracking-tight text-fg ${className}`} {...props}>
      {children}
    </h3>
  );
}

export function CardDescription({ className = '', children, ...props }) {
  return (
    <p className={`text-sm text-muted ${className}`} {...props}>
      {children}
    </p>
  );
}

export function CardContent({ className = '', children, ...props }) {
  return (
    <div className={`p-5 pt-2 ${className}`} {...props}>
      {children}
    </div>
  );
}

export function CardFooter({ className = '', children, ...props }) {
  return (
    <div className={`p-5 pt-3 ${className}`} {...props}>
      {children}
    </div>
  );
}
