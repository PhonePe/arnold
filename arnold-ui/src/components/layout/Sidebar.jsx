'use client';

import React from 'react';
import {
  Home,
  ListChecks,
  FileCheck2,
  Users,
  BarChart3,
  ShieldCheck,
  History,
  LogOut,
} from 'lucide-react';
import { Seal } from '@/components/ui/Seal';
import { RunButton } from './RunButton';

// Primary navigation. Governance items live in a labelled second group.
const defaultNavItems = [
  { key: 'dashboard', label: 'Home', icon: Home },
  { key: 'projects', label: 'Projects', icon: ListChecks },
  { key: 'stats', label: 'Stats', icon: BarChart3 },
  { key: 'knowledge', label: 'Knowledge Base', icon: Users },
  { key: 'history', label: 'Analysis History', icon: History, group: 'governance' },
];

const defaultBottomItems = [];

function NavButton({ item, isActive, collapsed, onNavigate }) {
  const Icon = item.icon;
  return (
    <button
      onClick={() => onNavigate?.(item.key)}
      title={collapsed ? item.label : undefined}
      className={`relative flex w-full items-center gap-[11px] rounded-none px-[10px] py-[9px] text-sm font-medium transition-colors ${
        collapsed ? 'justify-center' : ''
      } ${
        isActive
          ? 'bg-[var(--accent-soft)] text-accent-ink'
          : 'text-muted hover:bg-[var(--ink-wash)] hover:text-fg'
      }`}
    >
      {isActive && (
        <span
          className={`absolute top-[7px] bottom-[7px] w-[3px] bg-accent ${
            collapsed ? 'left-0' : '-left-[14px]'
          }`}
        />
      )}
      {Icon && <Icon className="h-[17px] w-[17px] flex-none" strokeWidth={1.8} />}
      {!collapsed && <span className="truncate">{item.label}</span>}
      {!collapsed && item.badge != null && (
        <span
          className={`ml-auto rounded-full px-[7px] py-px font-mono text-[11px] ${
            isActive ? 'bg-accent text-on-accent' : 'bg-fg text-surface'
          }`}
        >
          {item.badge}
        </span>
      )}
    </button>
  );
}

export function Sidebar({
  navItems = defaultNavItems,
  bottomItems = defaultBottomItems,
  activeKey = 'dashboard',
  collapsed = false,
  onToggleCollapse,
  onNavigate,
  onRunAnalysis,
  user,
  onLogout,
  className = '',
  ...props
}) {
  const primary = navItems.filter((i) => i.group !== 'governance');
  const governance = navItems.filter((i) => i.group === 'governance');
  const initials = user?.initials || 'A';

  return (
    <aside
      className={`flex h-full flex-col gap-1.5 border-r border-border bg-surface px-[14px] py-[18px] transition-all duration-200 ${
        collapsed ? 'w-[76px] items-stretch' : 'w-[248px]'
      } ${className}`}
      {...props}
    >
      {/* Brand */}
      <div className={`flex items-center gap-[11px] px-2 pb-4 pt-1 ${collapsed ? 'justify-center px-0' : ''}`}>
        <Seal size={38} />
        {!collapsed && (
          <div className="min-w-0 leading-none">
            <div className="font-display text-[18px] font-bold leading-none tracking-[-0.01em] text-fg">
              Arnold
            </div>
            <div className="mt-1 font-mono text-[10px] uppercase tracking-[0.14em] text-muted">
              Code Review
            </div>
          </div>
        )}
      </div>

      {/* Primary nav */}
      <nav className="flex flex-col gap-0.5">
        {primary.map((item) => (
          <NavButton
            key={item.key}
            item={item}
            isActive={item.key === activeKey}
            collapsed={collapsed}
            onNavigate={onNavigate}
          />
        ))}
      </nav>

      {/* Governance group */}
      {governance.length > 0 && (
        <>
          <div
            className={`px-[10px] pb-1 pt-3 font-mono text-[10px] uppercase tracking-[0.14em] text-faint ${
              collapsed ? 'text-center' : ''
            }`}
          >
            {collapsed ? '···' : 'Governance'}
          </div>
          <nav className="flex flex-col gap-0.5">
            {governance.map((item) => (
              <NavButton
                key={item.key}
                item={item}
                isActive={item.key === activeKey}
                collapsed={collapsed}
                onNavigate={onNavigate}
              />
            ))}
          </nav>
        </>
      )}

      {/* Footer: Run Analysis CTA + user chip */}
      <div className="mt-auto flex flex-col gap-2.5 pt-3">
        <RunButton collapsed={collapsed} onClick={onRunAnalysis} />

        <div className={`flex items-center gap-2.5 px-1 py-1.5 ${collapsed ? 'justify-center' : ''}`}>
          <span className="inline-flex h-[30px] w-[30px] flex-none items-center justify-center rounded-full bg-fg font-mono text-[11px] font-bold text-surface">
            {initials}
          </span>
          {!collapsed && (
            <>
              <div className="min-w-0 leading-[1.2]">
                <div className="truncate text-[13px] font-semibold text-fg">
                  {user?.name || 'Signed in'}
                </div>
                <div className="truncate font-mono text-[11px] text-muted">
                  {user?.subtitle || 'Reviewer'}
                </div>
              </div>
              {onLogout && (
                <button
                  onClick={onLogout}
                  title="Log out"
                  aria-label="Log out"
                  className="ml-auto grid h-[30px] w-[30px] flex-none place-items-center border border-transparent text-muted hover:bg-[var(--ink-wash)] hover:text-accent"
                >
                  <LogOut className="h-[17px] w-[17px]" strokeWidth={1.8} />
                </button>
              )}
            </>
          )}
        </div>
      </div>
    </aside>
  );
}

// Re-export the lucide icons used as defaults so callers can build nav items
// without re-importing.
export { Home, ListChecks, FileCheck2, Users, BarChart3, ShieldCheck, History };
