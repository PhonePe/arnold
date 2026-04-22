'use client';

import React from 'react';

export function Input({ className = '', ...props }) {
  return (
    <input
      className={
        'flex h-10 w-full rounded-sm bg-surface-container-highest px-3 py-2 text-sm text-on-surface ' +
        'placeholder:text-on-surface-faint ' +
        'border-b-2 border-transparent ' +
        'focus-visible:outline-none focus-visible:border-b-primary ' +
        'disabled:cursor-not-allowed disabled:opacity-50 ' +
        'transition-colors ' +
        className
      }
      {...props}
    />
  );
}
