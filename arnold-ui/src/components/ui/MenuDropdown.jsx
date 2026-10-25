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

import React, { useEffect, useRef, useState } from 'react';
import { ChevronDown } from 'lucide-react';

export function MenuDropdown({ label, icon: Icon, options, onSelect, disabled = false, className = '', children, ...props }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    function onPointerDown(event) {
      if (ref.current && !ref.current.contains(event.target)) setOpen(false);
    }
    function onKey(event) {
      if (event.key === 'Escape') setOpen(false);
    }
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div className={`relative ${className}`} ref={ref} data-open={open} {...props}>
      <button
        type="button"
        className="flex h-8 items-center gap-1.5 border border-border-2 px-2 font-mono text-[11px] font-semibold text-muted bg-surface transition-colors hover:bg-surface-2 hover:text-fg active:translate-y-px focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={label}
        title={label}
        disabled={disabled}
        onClick={() => setOpen((state) => !state)}
      >
        {Icon ? <Icon className="h-4 w-4" aria-hidden="true" /> : null}
        <ChevronDown className="h-3.5 w-3.5 opacity-70" aria-hidden="true" />
      </button>
      {open ? (
        <div role="menu" aria-label={label} className="absolute right-0 z-30 mt-1 min-w-56 border border-border-2 bg-surface py-1 shadow-lg">
          {children || options.map((option) => (
            <button
              key={option.value}
              type="button"
              role="menuitemradio"
              aria-checked={option.value === option.current}
              className={`flex w-full items-center gap-2 px-3 py-1.5 text-left font-mono text-[12px] ${option.value === option.current ? 'bg-[var(--accent-soft)] text-fg' : 'text-muted hover:bg-surface-2 hover:text-fg'}`}
              onClick={() => { onSelect(option.value); setOpen(false); }}
            >
              <span className={`h-1.5 w-1.5 ${option.value === option.current ? 'bg-accent' : 'bg-transparent'}`} aria-hidden="true" />
              {option.label}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}
