'use client';

import React from 'react';

export function Tabs({
  items = [],
  activeKey,
  onTabChange,
  className = '',
  ...props
}) {
  return (
    <nav className={`flex items-center gap-6 ${className}`} {...props}>
      {items.map((item) => {
        const isActive = item.key === activeKey;
        return (
          <button
            key={item.key}
            onClick={() => onTabChange?.(item.key)}
            className={`relative pb-2 text-sm font-medium font-body transition-colors ${
              isActive
                ? 'text-on-surface'
                : 'text-on-surface-muted hover:text-on-surface'
            }`}
          >
            {item.label}
            {isActive && (
              <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary rounded-full" />
            )}
          </button>
        );
      })}
    </nav>
  );
}
