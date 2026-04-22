'use client';

import React from 'react';

export function Label({ className = '', children, ...props }) {
  return (
    <label
      className={`text-sm font-medium font-body leading-none text-on-surface peer-disabled:cursor-not-allowed peer-disabled:opacity-70 ${className}`}
      {...props}
    >
      {children}
    </label>
  );
}
