'use client';

/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

import React, { forwardRef } from 'react';

// Lattice buttons: sharp corners, flat vermilion primary, hairline-bordered
// secondary. Borders are a first-class part of this system.
const variants = {
  primary:
    'bg-accent text-on-accent font-semibold hover:bg-accent-ink',
  secondary:
    'border border-border-2 bg-surface text-fg hover:border-fg',
  inverted:
    'bg-fg text-surface hover:opacity-90',
  outline:
    'border border-border-2 bg-transparent text-fg hover:border-fg',
  ghost:
    'bg-transparent text-muted hover:bg-[var(--ink-wash)] hover:text-accent',
  ok:
    'bg-ok text-on-accent hover:brightness-95',
  danger:
    'bg-transparent border border-border-2 text-bad hover:border-bad',
  link:
    'bg-transparent text-accent-ink underline-offset-4 hover:underline p-0 h-auto border-0',
};

const sizes = {
  xs: 'h-7 px-2.5 text-[11.5px] gap-1.5',
  sm: 'h-8 px-3 text-xs gap-1.5',
  md: 'h-9 px-4 text-[13px] gap-2',
  lg: 'h-11 px-6 text-sm gap-2',
};

export const Button = forwardRef(function Button({
  variant = 'primary',
  size = 'md',
  className = '',
  children,
  ...props
}, ref) {
  const base =
    'inline-flex items-center justify-center whitespace-nowrap rounded-none font-semibold transition-colors active:translate-y-px ' +
    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-bg ' +
    'disabled:pointer-events-none disabled:opacity-50';

  return (
    <button
      ref={ref}
      className={`${base} ${sizes[size] || sizes.md} ${variants[variant] || variants.primary} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
});
