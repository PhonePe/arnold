'use client';

import React from 'react';
import { Sidebar } from './Sidebar';
import { TopNav } from './TopNav';
import { StatusBar } from './StatusBar';

export function AppShell({
  sidebarProps = {},
  topNavProps = {},
  statusBarProps,
  children,
  className = '',
  ...props
}) {
  return (
    <div className={`flex h-screen overflow-hidden bg-surface ${className}`} {...props}>
      {/* Sidebar */}
      <Sidebar {...sidebarProps} />

      {/* Main area */}
      <div className="flex flex-col flex-1 overflow-hidden">
        {/* Top navigation */}
        <TopNav {...topNavProps} />

        {/* Content */}
        <main className="flex-1 overflow-y-auto p-6">
          {children}
        </main>

        {/* Status bar */}
        {statusBarProps && <StatusBar {...statusBarProps} />}
      </div>
    </div>
  );
}
