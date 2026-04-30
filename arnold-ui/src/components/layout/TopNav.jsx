'use client';

import React from 'react';
import { Bell, Settings } from 'lucide-react';
import { SearchInput } from '@/components/ui/SearchInput';
import { Tabs } from '@/components/ui/Tabs';
import { Avatar } from '@/components/ui/Avatar';

export function TopNav({
  breadcrumbs = [],
  tabs = [],
  activeTab,
  onTabChange,
  searchPlaceholder = 'Search...',
  onSearch,
  userInitials = 'U',
  userAvatar,
  className = '',
  ...props
}) {
  return (
    <header
      className={`sticky top-0 z-40 flex items-center justify-between h-14 px-6 bg-surface-container-low/80 backdrop-blur ${className}`}
      {...props}
    >
      {/* Left: breadcrumbs + tabs */}
      <div className="flex items-center gap-6">
        {/* Breadcrumbs */}
        {breadcrumbs.length > 0 && (
          <div className="flex items-center gap-1.5 text-sm font-body">
            {breadcrumbs.map((crumb, i) => (
              <React.Fragment key={i}>
                {i > 0 && <span className="text-on-surface-faint">&rsaquo;</span>}
                {crumb.href ? (
                  <a
                    href={crumb.href}
                    className="text-on-surface-muted hover:text-primary transition-colors"
                  >
                    {crumb.label}
                  </a>
                ) : (
                  <span className="text-on-surface font-medium">{crumb.label}</span>
                )}
              </React.Fragment>
            ))}
          </div>
        )}

        {/* Tabs */}
        {tabs.length > 0 && (
          <Tabs items={tabs} activeKey={activeTab} onTabChange={onTabChange} />
        )}
      </div>

      {/* Right: search + actions + avatar */}
      <div className="flex items-center gap-3">
        <SearchInput
          placeholder={searchPlaceholder}
          className="w-56"
          onChange={(e) => onSearch?.(e.target.value)}
        />

        <button
          className="p-2 rounded-sm text-on-surface-muted hover:text-on-surface hover:bg-surface-container transition-colors"
          aria-label="Notifications"
        >
          <Bell className="w-[18px] h-[18px]" />
        </button>

        <button
          className="p-2 rounded-sm text-on-surface-muted hover:text-on-surface hover:bg-surface-container transition-colors"
          aria-label="Settings"
        >
          <Settings className="w-[18px] h-[18px]" />
        </button>

        <Avatar src={userAvatar} initials={userInitials} size="md" />
      </div>
    </header>
  );
}
