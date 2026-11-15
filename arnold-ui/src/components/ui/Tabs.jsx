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

// Lattice tabs. Two variants:
//   - `underline` (default): mono-ish text with a sharp accent rail under the
//     active tab.
//   - `segment`: a boxed segmented control (ink-wash track, raised active chip).
export function Tabs({
  items = [],
  activeKey,
  onTabChange,
  variant = 'underline',
  className = '',
  ...props
}) {
  if (variant === 'segment') {
    return (
      <div
        className={`inline-flex gap-0.5 border border-border bg-[var(--ink-wash)] p-[3px] ${className}`}
        {...props}
      >
        {items.map((item) => {
          const isActive = item.key === activeKey;
          return (
            <button
              key={item.key}
              onClick={() => onTabChange?.(item.key)}
              className={`rounded-none px-3 py-1.5 font-mono text-[12px] font-semibold tracking-[0.02em] transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
                isActive
                  ? 'bg-surface-2 text-fg shadow-sm'
                  : 'text-muted hover:text-fg'
              }`}
            >
              {item.label}
            </button>
          );
        })}
      </div>
    );
  }

  return (
    <nav className={`flex items-center gap-6 ${className}`} {...props}>
      {items.map((item) => {
        const isActive = item.key === activeKey;
        return (
          <button
            key={item.key}
            onClick={() => onTabChange?.(item.key)}
            className={`relative pb-2 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
              isActive ? 'text-fg' : 'text-muted hover:text-fg'
            }`}
          >
            {item.label}
            {isActive && (
              <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-accent" />
            )}
          </button>
        );
      })}
    </nav>
  );
}
