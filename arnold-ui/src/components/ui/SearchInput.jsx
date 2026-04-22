'use client';

import React from 'react';
import { Search } from 'lucide-react';

export function SearchInput({
  placeholder = 'Search...',
  className = '',
  ...props
}) {
  return (
    <div className={`relative ${className}`}>
      <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-on-surface-faint pointer-events-none" />
      <input
        type="text"
        placeholder={placeholder}
        className={
          'h-9 w-full rounded-sm bg-surface-container-highest pl-9 pr-3 text-sm text-on-surface ' +
          'placeholder:text-on-surface-faint ' +
          'border-b-2 border-transparent ' +
          'focus-visible:outline-none focus-visible:border-b-primary ' +
          'transition-colors'
        }
        {...props}
      />
    </div>
  );
}
