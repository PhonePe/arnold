'use client';

import React from 'react';
import { Sidebar } from './Sidebar';
import { TopNav } from './TopNav';
import { StatusBar } from './StatusBar';

// Lattice app shell. The main canvas stays transparent so the body's engraved
// lattice grid shows through behind cards and panels.
export function AppShell({
  sidebarProps = {},
  topNavProps = {},
  statusBarProps,
  children,
  className = '',
  ...props
}) {
  return (
    <div className={`flex h-screen overflow-hidden ${className}`} {...props}>
      <Sidebar {...sidebarProps} />

      <div className="flex flex-1 flex-col overflow-hidden">
        <TopNav {...topNavProps} />

        <main className="flex-1 overflow-y-auto px-10 py-8">
          {children}
        </main>

        {statusBarProps && <StatusBar {...statusBarProps} />}
      </div>
    </div>
  );
}
