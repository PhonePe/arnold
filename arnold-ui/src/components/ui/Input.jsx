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

// Lattice inputs: hairline-bordered on the highest surface, accent border +
// soft ring on focus. Sharp corners.
export function Input({ className = '', ...props }) {
  return (
    <input
      className={
        'flex h-9 w-full rounded-none border border-border-2 bg-surface-2 px-3 py-2 text-[13px] text-fg ' +
        'placeholder:text-faint ' +
        'focus-visible:outline-none focus-visible:border-accent focus-visible:ring-[3px] focus-visible:ring-[var(--accent-soft)] ' +
        'disabled:cursor-not-allowed disabled:opacity-50 ' +
        'transition-colors ' +
        className
      }
      {...props}
    />
  );
}
