'use client';

/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React, { useEffect, useRef, useState } from 'react';
import {
  Home,
  ListChecks,
  FileCheck2,
  Users,
  BarChart3,
  ShieldCheck,
  History,
  LogOut,
  Github,
  Gitlab,
  TriangleAlert,
  Menu,
  Settings,
} from 'lucide-react';
import { Seal } from '@/components/ui/Seal';
import { Avatar } from '@/components/ui/Avatar';
import { RunButton } from './RunButton';

// Primary navigation. Governance items live in a labelled second group.
const defaultNavItems = [
  { key: 'dashboard', label: 'Home', icon: Home },
  { key: 'projects', label: 'Projects', icon: ListChecks },
  { key: 'stats', label: 'Organisation Insights', icon: BarChart3 },
  { key: 'knowledge', label: 'Knowledge Base', icon: Users },
  { key: 'history', label: 'Analysis History', icon: History, group: 'governance' },
];

const defaultBottomItems = [];
const PROVIDER_ICONS = { GITHUB: Github, GITLAB: Gitlab };

function ConnectionLine({ connection }) {
  if (connection === undefined) {
    return <div className="truncate font-mono text-[11px] text-faint">Checking connections…</div>;
  }
  if (connection === null) {
    return (
      <div className="flex items-center gap-1.5 truncate font-mono text-[11px] text-accent-ink">
        <TriangleAlert className="h-3 w-3 flex-none" strokeWidth={1.8} aria-hidden="true" />
        Setup required
      </div>
    );
  }
  return (
    <div className="flex items-center gap-1.5 truncate font-mono text-[11px] text-ok">
      {connection.providers.map((provider) => {
        const Icon = PROVIDER_ICONS[provider] || Github;
        return <Icon key={provider} className="h-3 w-3 flex-none" strokeWidth={1.8} aria-hidden="true" />;
      })}
      <span className="truncate">{connection.label}</span>
    </div>
  );
}

function NavButton({ item, isActive, collapsed, onNavigate }) {
  const Icon = item.icon;
  return (
    <button
      onClick={() => onNavigate?.(item.key)}
      title={collapsed ? item.label : undefined}
      className={`relative flex w-full items-center gap-[11px] rounded-none px-[10px] py-[9px] text-sm font-medium transition-[background,color,transform] duration-100 active:scale-[0.99] ${
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
  connection,
  onOpenSetup,
  onOpenSettings,
  onLogout,
  className = '',
  ...props
}) {
  const primary = navItems.filter((i) => i.group !== 'governance');
  const governance = navItems.filter((i) => i.group === 'governance');
  const initials = user?.initials || 'A';
  const [accountOpen, setAccountOpen] = useState(false);
  const accountRef = useRef(null);

  useEffect(() => {
    if (!accountOpen) return;
    const close = (event) => {
      if (event.key === 'Escape' || !accountRef.current?.contains(event.target)) setAccountOpen(false);
    };
    document.addEventListener('keydown', close);
    document.addEventListener('pointerdown', close);
    return () => {
      document.removeEventListener('keydown', close);
      document.removeEventListener('pointerdown', close);
    };
  }, [accountOpen]);

  function runAccountAction(action) {
    setAccountOpen(false);
    action?.();
  }

  return (
    <aside
      className={`flex h-full flex-col gap-1.5 border-r border-border bg-surface px-[14px] py-[18px] transition-all duration-200 ${
        collapsed ? 'w-[76px] items-stretch' : 'w-[248px]'
      } ${className}`}
      {...props}
    >
      {/* Brand — clicking returns home */}
      <button
        type="button"
        onClick={() => onNavigate?.('home')}
        title="Go to home"
        className={`flex items-center gap-[11px] rounded-none px-2 pb-4 pt-1 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${collapsed ? 'justify-center px-0' : 'hover:bg-[var(--ink-wash)]'}`}
      >
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
      </button>

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

        {connection === null && onOpenSetup ? (
          <button
            type="button"
            onClick={onOpenSetup}
            title="Open the setup guide"
            className={`flex items-center gap-2 border border-accent bg-[var(--accent-soft)] px-2.5 py-2 font-mono text-[11px] uppercase tracking-[0.06em] text-accent-ink transition-colors hover:bg-accent hover:text-on-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${collapsed ? 'justify-center' : ''}`}
          >
            <TriangleAlert className="h-3.5 w-3.5 flex-none" strokeWidth={1.8} aria-hidden="true" />
            {collapsed ? <span className="sr-only">Finish setup</span> : 'Finish setup'}
          </button>
        ) : null}

        <div
          ref={accountRef}
          className={`group relative flex items-center gap-2.5 px-1 py-1.5 transition-colors hover:bg-[var(--ink-wash)] focus-within:bg-[var(--ink-wash)] ${collapsed ? 'justify-center' : ''}`}
        >
          <Avatar src={user?.avatarUrl} alt={user?.name || 'Signed in'} initials={initials} />
          {!collapsed && (
            <div className={`min-w-0 flex-1 leading-[1.2] transition-all duration-150 motion-reduce:transform-none ${accountOpen ? '-translate-x-1 opacity-0' : 'group-hover:-translate-x-1 group-hover:opacity-0 group-focus-within:-translate-x-1 group-focus-within:opacity-0'}`}>
                <div className="truncate text-[13px] font-semibold text-fg">
                  {user?.name || 'Signed in'}
                </div>
                <ConnectionLine connection={connection} />
            </div>
          )}
          <div
            id="account-actions"
            className={`absolute z-20 flex items-center border border-border-2 bg-surface-2 p-1 shadow-md transition-all duration-150 ${
              collapsed ? 'bottom-0 left-[calc(100%+8px)]' : 'right-9 top-1/2 -translate-y-1/2 border-0 bg-gradient-to-r from-transparent via-surface to-surface shadow-none'
            } ${accountOpen ? 'pointer-events-auto translate-x-0 opacity-100' : 'pointer-events-none translate-x-2 opacity-0 group-hover:pointer-events-auto group-hover:translate-x-0 group-hover:opacity-100 group-focus-within:pointer-events-auto group-focus-within:translate-x-0 group-focus-within:opacity-100'}`}
          >
            <button
              type="button"
              onClick={() => runAccountAction(onOpenSettings)}
              title="Settings"
              aria-label="Open settings"
              className="grid h-[30px] w-[30px] place-items-center border border-transparent text-muted transition-colors hover:bg-[var(--ink-wash)] hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <Settings className="h-[17px] w-[17px]" strokeWidth={1.8} />
            </button>
            {onLogout ? (
              <button
                type="button"
                onClick={() => runAccountAction(onLogout)}
                title="Sign out"
                aria-label="Sign out"
                className="grid h-[30px] w-[30px] place-items-center border border-transparent text-muted transition-colors hover:bg-[color-mix(in_oklch,var(--bad)_12%,transparent)] hover:text-bad focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <LogOut className="h-[17px] w-[17px]" strokeWidth={1.8} />
              </button>
            ) : null}
          </div>
          <button
            type="button"
            title="Account actions"
            aria-label="Account actions"
            aria-haspopup="true"
            aria-expanded={accountOpen}
            aria-controls="account-actions"
            onClick={(event) => {
              event.stopPropagation();
              setAccountOpen((value) => !value);
            }}
            className={`${collapsed ? 'absolute inset-0 h-full w-full opacity-0' : 'ml-auto'} z-30 grid h-[30px] w-[30px] flex-none place-items-center border border-transparent text-muted transition-all duration-200 motion-reduce:transform-none hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${accountOpen ? 'rotate-90 text-accent' : 'group-hover:rotate-90 group-focus-within:rotate-90'}`}
          >
            <Menu className="h-[18px] w-[18px]" strokeWidth={1.9} />
          </button>
        </div>
      </div>
    </aside>
  );
}

// Re-export the lucide icons used as defaults so callers can build nav items
// without re-importing.
export { Home, ListChecks, FileCheck2, Users, BarChart3, ShieldCheck, History };
