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
