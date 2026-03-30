'use client';

import React from 'react';
import {
  LayoutDashboard,
  FolderKanban,
  TrendingUp,
  BookOpen,
  Clock,
  Globe,
  HelpCircle,
  Zap,
  PanelLeftClose,
  PanelLeftOpen,
} from 'lucide-react';
import { Button } from '@/components/ui/Button';

const defaultNavItems = [
  { key: 'dashboard', label: 'DASHBOARD', icon: LayoutDashboard },
  { key: 'projects', label: 'PROJECTS', icon: FolderKanban },
  { key: 'stats', label: 'STATS', icon: TrendingUp },
  { key: 'knowledge', label: 'KNOWLEDGE BASE', icon: BookOpen },
  { key: 'history', label: 'ANALYSIS HISTORY', icon: Clock },
];

const defaultBottomItems = [
  { key: 'agent-status', label: 'AGENT STATUS', icon: Globe },
  { key: 'help', label: 'HELP', icon: HelpCircle },
];

export function Sidebar({
  navItems = defaultNavItems,
  bottomItems = defaultBottomItems,
  activeKey = 'dashboard',
  collapsed = false,
  onToggleCollapse,
  onNavigate,
  onRunAnalysis,
  className = '',
  ...props
}) {
  return (
    <aside
      className={`flex flex-col h-full bg-surface-container-low transition-all duration-200 ${
        collapsed ? 'w-16' : 'w-56'
      } ${className}`}
      {...props}
    >
      {/* Brand */}
      <div className={`p-4 pb-6 ${collapsed ? 'px-3' : ''}`}>
        <div className="flex items-center gap-2">
          <span className="inline-flex items-center justify-center w-8 h-8 rounded-sm cta-gradient text-on-primary text-sm font-bold flex-shrink-0">
            A
          </span>
          {!collapsed && (
            <div className="min-w-0">
              <p className="text-sm font-semibold font-headline text-on-surface truncate">Arnold AI</p>
              <p className="text-[10px] uppercase tracking-widest text-on-surface-faint font-body truncate">
                The Sentient Ledger
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Main nav */}
      <nav className="flex-1 px-2 space-y-0.5">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = item.key === activeKey;
          return (
            <button
              key={item.key}
              onClick={() => onNavigate?.(item.key)}
              title={collapsed ? item.label : undefined}
              className={`flex items-center gap-3 w-full rounded-sm px-3 py-2 text-xs font-medium font-body transition-colors ${
                collapsed ? 'justify-center' : ''
              } ${
                isActive
                  ? 'text-primary bg-primary/10'
                  : 'text-on-surface-muted hover:text-on-surface hover:bg-surface-container'
              }`}
            >
              {Icon && <Icon className="w-[18px] h-[18px] flex-shrink-0" />}
              {!collapsed && <span className="truncate">{item.label}</span>}
            </button>
          );
        })}
      </nav>

      {/* CTA */}
      <div className={`px-3 pb-3 ${collapsed ? 'px-2' : ''}`}>
        {collapsed ? (
          <button
            onClick={onRunAnalysis}
            title="RUN ANALYSIS"
            className="flex items-center justify-center w-full h-10 rounded-sm cta-gradient text-on-primary transition-colors"
          >
            <Zap className="w-4 h-4" />
          </button>
        ) : (
          <Button
            variant="primary"
            size="md"
            className="w-full justify-center gap-2"
            onClick={onRunAnalysis}
          >
            <Zap className="w-4 h-4" />
            RUN ANALYSIS
          </Button>
        )}
      </div>

      {/* Bottom nav */}
      <div className="px-2 pb-2 space-y-0.5">
        {bottomItems.map((item) => {
          const Icon = item.icon;
          return (
            <button
              key={item.key}
              onClick={() => onNavigate?.(item.key)}
              title={collapsed ? item.label : undefined}
              className={`flex items-center gap-3 w-full rounded-sm px-3 py-2 text-xs font-medium font-body text-on-surface-muted hover:text-on-surface hover:bg-surface-container transition-colors ${
                collapsed ? 'justify-center' : ''
              }`}
            >
              {Icon && <Icon className="w-[18px] h-[18px] flex-shrink-0" />}
              {!collapsed && <span className="truncate">{item.label}</span>}
            </button>
          );
        })}
      </div>

      {/* Collapse toggle */}
      <div className={`px-2 pb-3 ${collapsed ? 'flex justify-center' : ''}`}>
        <button
          onClick={onToggleCollapse}
          className="flex items-center justify-center w-full rounded-sm p-2 text-on-surface-faint hover:text-on-surface hover:bg-surface-container transition-colors"
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          {collapsed ? (
            <PanelLeftOpen className="w-4 h-4" />
          ) : (
            <PanelLeftClose className="w-4 h-4" />
          )}
        </button>
      </div>
    </aside>
  );
}
