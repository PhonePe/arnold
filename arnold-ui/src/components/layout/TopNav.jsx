'use client';

import React from 'react';
import { Bell, PanelLeft } from 'lucide-react';
import { SearchInput } from '@/components/ui/SearchInput';
import { ThemeToggle } from './ThemeToggle';

// Lattice top bar: collapse toggle, mono breadcrumb, search pushed right,
// notifications, theme toggle. Hairline bottom border + frosted backdrop.
export function TopNav({
  breadcrumbs = [],
  searchPlaceholder = 'Search...',
  onSearch,
  onToggleCollapse,
  className = '',
  ...props
}) {
  return (
    <header
      className={`sticky top-0 z-30 flex h-[60px] items-center gap-4 border-b border-border bg-[color-mix(in_oklch,var(--bg)_86%,transparent)] px-6 backdrop-blur-[10px] ${className}`}
      {...props}
    >
      {onToggleCollapse && (
        <button
          onClick={onToggleCollapse}
          aria-label="Toggle sidebar"
          title="Toggle sidebar"
          className="grid h-[34px] w-[34px] place-items-center rounded-none border border-transparent text-muted transition-colors hover:bg-[var(--ink-wash)] hover:text-fg"
        >
          <PanelLeft className="h-[18px] w-[18px]" strokeWidth={1.8} />
        </button>
      )}

      {/* Breadcrumb (mono) */}
      {breadcrumbs.length > 0 && (
        <div className="font-mono text-[12px] tracking-[0.02em] text-muted">
          {breadcrumbs.map((crumb, i) => (
            <React.Fragment key={i}>
              {i > 0 && <span className="px-1.5 opacity-50">/</span>}
              {i === breadcrumbs.length - 1 ? (
                <span className="font-semibold text-fg">{crumb.label}</span>
              ) : crumb.href ? (
                <a href={crumb.href} className="transition-colors hover:text-accent">
                  {crumb.label}
                </a>
              ) : (
                <span>{crumb.label}</span>
              )}
            </React.Fragment>
          ))}
        </div>
      )}

      {/* Right cluster */}
      <div className="ml-auto flex items-center gap-2">
        <SearchInput
          placeholder={searchPlaceholder}
          className="w-[min(320px,34vw)]"
          onChange={(e) => onSearch?.(e.target.value)}
        />

        <button
          className="relative grid h-[34px] w-[34px] place-items-center rounded-none border border-transparent text-muted transition-colors hover:bg-[var(--ink-wash)] hover:text-fg"
          aria-label="Notifications"
          title="Notifications"
        >
          <Bell className="h-[18px] w-[18px]" strokeWidth={1.8} />
          <span className="absolute right-[7px] top-[6px] h-[7px] w-[7px] rounded-full bg-accent" />
        </button>

        <ThemeToggle />
      </div>
    </header>
  );
}
