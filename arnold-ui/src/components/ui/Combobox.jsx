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

import React, { useEffect, useId, useMemo, useRef, useState } from 'react';
import { Check, ChevronDown, Search } from 'lucide-react';

const SIZES = {
  sm: 'h-9 text-[13px]',
  md: 'h-10 text-sm',
};

function Highlighted({ segments, text }) {
  if (!segments) return text;
  return segments.map((segment, index) => segment.match
    ? <mark key={index} className="bg-transparent font-semibold text-accent-ink">{segment.text}</mark>
    : <React.Fragment key={index}>{segment.text}</React.Fragment>);
}

function nextEnabled(items, from, step) {
  for (let index = from; index >= 0 && index < items.length; index += step) {
    if (!items[index].disabled) return index;
  }
  return -1;
}

/**
 * Lattice select/combobox. Options: `{ value, label, secondary?, badge?, disabled?, note? }`.
 * With `filterOptions(query, options)` a search box is shown; it may return options carrying
 * `labelSegments` / `secondarySegments` (`[{ text, match }]`) for highlighting.
 */
export function Combobox({
  id,
  options = [],
  value,
  onChange,
  placeholder = 'Select…',
  disabled = false,
  filterOptions,
  searchPlaceholder = 'Search…',
  emptyText = 'No matches',
  size = 'md',
  className = '',
  'aria-label': ariaLabel,
  'aria-labelledby': ariaLabelledBy,
  'aria-describedby': ariaDescribedBy,
  ...props
}) {
  const generatedId = useId();
  const baseId = id || `combobox-${generatedId.replace(/:/g, '')}`;
  const listId = `${baseId}-listbox`;
  const rootRef = useRef(null);
  const triggerRef = useRef(null);
  const searchRef = useRef(null);
  const listRef = useRef(null);
  const typeaheadRef = useRef({ text: '', timer: 0 });
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(-1);
  const searchable = typeof filterOptions === 'function';
  const selected = options.find((option) => option.value === value);
  const items = useMemo(() => searchable && query.trim() ? filterOptions(query, options) : options, [filterOptions, options, query, searchable]);
  const optionId = (index) => `${baseId}-option-${index}`;

  function openList(seed = '') {
    if (disabled) return;
    const nextQuery = searchable ? seed : '';
    const visible = searchable && nextQuery.trim() ? filterOptions(nextQuery, options) : options;
    const selectedIndex = nextQuery ? -1 : visible.findIndex((option) => option.value === value && !option.disabled);
    setQuery(nextQuery);
    setActiveIndex(selectedIndex >= 0 ? selectedIndex : nextEnabled(visible, 0, 1));
    setOpen(true);
  }

  function close(refocus = true) {
    setOpen(false);
    setQuery('');
    if (refocus) triggerRef.current?.focus();
  }

  function choose(option) {
    if (!option || option.disabled) return;
    if (option.value !== value) onChange?.(option.value, option);
    close();
  }

  useEffect(() => {
    if (!open) return undefined;
    (searchable ? searchRef.current : listRef.current)?.focus();
    function onPointerDown(event) {
      if (rootRef.current && !rootRef.current.contains(event.target)) {
        setOpen(false);
        setQuery('');
      }
    }
    document.addEventListener('mousedown', onPointerDown);
    return () => document.removeEventListener('mousedown', onPointerDown);
  }, [open, searchable]);

  useEffect(() => {
    if (open && activeIndex >= 0) document.getElementById(optionId(activeIndex))?.scrollIntoView({ block: 'nearest' });
  });

  function typeahead(character) {
    const state = typeaheadRef.current;
    window.clearTimeout(state.timer);
    state.text += character.toLowerCase();
    state.timer = window.setTimeout(() => { state.text = ''; }, 600);
    const start = activeIndex + (state.text.length === 1 ? 1 : 0);
    const ordered = [...items.slice(start), ...items.slice(0, start)];
    const match = ordered.find((option) => !option.disabled && String(option.label).toLowerCase().startsWith(state.text));
    if (match) setActiveIndex(items.indexOf(match));
  }

  function onListKeyDown(event) {
    const printable = event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey;
    switch (event.key) {
      case 'ArrowDown': {
        const next = nextEnabled(items, activeIndex + 1, 1);
        if (next >= 0) setActiveIndex(next);
        break;
      }
      case 'ArrowUp': {
        const next = nextEnabled(items, activeIndex - 1, -1);
        if (next >= 0) setActiveIndex(next);
        break;
      }
      case 'Home':
        if (searchable && event.target === searchRef.current && !event.ctrlKey) return;
        setActiveIndex(nextEnabled(items, 0, 1));
        break;
      case 'End':
        if (searchable && event.target === searchRef.current && !event.ctrlKey) return;
        setActiveIndex(nextEnabled(items, items.length - 1, -1));
        break;
      case 'Enter':
        choose(items[activeIndex]);
        break;
      case 'Escape':
        close();
        break;
      case 'Tab':
        close(false);
        return;
      default:
        if (printable && !searchable) {
          typeahead(event.key);
          break;
        }
        return;
    }
    event.preventDefault();
  }

  function onTriggerKeyDown(event) {
    if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) {
      event.preventDefault();
      openList();
    } else if (searchable && event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
      event.preventDefault();
      openList(event.key);
    }
  }

  const activeDescendant = open && activeIndex >= 0 && items[activeIndex] ? optionId(activeIndex) : undefined;

  return (
    <div ref={rootRef} className={`relative min-w-0 text-left ${className}`} {...props}>
      <button
        ref={triggerRef}
        id={baseId}
        type="button"
        role="combobox"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        aria-label={ariaLabel}
        aria-labelledby={ariaLabelledBy}
        aria-describedby={ariaDescribedBy}
        disabled={disabled}
        onClick={() => (open ? close() : openList())}
        onKeyDown={onTriggerKeyDown}
        className={`flex w-full min-w-0 items-center gap-2 rounded-none border bg-surface-2 px-3 text-left text-fg transition-colors focus-visible:border-accent focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-[var(--accent-soft)] disabled:cursor-not-allowed disabled:opacity-50 ${open ? 'border-accent' : 'border-border-2 hover:border-fg'} ${SIZES[size] || SIZES.md}`}
      >
        <span className="min-w-0 flex-1 truncate">
          {selected ? <>{selected.label}{selected.disabled && selected.note ? <span className="ml-2 font-mono text-[11px] text-faint">{selected.note}</span> : null}</> : <span className="text-faint">{placeholder}</span>}
        </span>
        <ChevronDown className={`h-4 w-4 shrink-0 text-muted transition-transform ${open ? 'rotate-180' : ''}`} strokeWidth={1.8} aria-hidden="true" />
      </button>

      {open ? (
        <div className="absolute left-0 right-0 z-30 mt-1 min-w-[16rem] rounded-none border border-border-2 bg-surface shadow-[0_14px_38px_rgba(28,26,22,0.16)]">
          {searchable ? (
            <label className="flex items-center gap-2 border-b border-border px-3">
              <Search className="h-3.5 w-3.5 shrink-0 text-faint" strokeWidth={1.8} aria-hidden="true" />
              <input
                ref={searchRef}
                type="text"
                role="combobox"
                aria-expanded="true"
                aria-controls={listId}
                aria-autocomplete="list"
                aria-activedescendant={activeDescendant}
                aria-label={searchPlaceholder}
                autoComplete="off"
                spellCheck={false}
                value={query}
                placeholder={searchPlaceholder}
                onChange={(event) => {
                  const nextQuery = event.target.value;
                  setQuery(nextQuery);
                  setActiveIndex(nextEnabled(nextQuery.trim() ? filterOptions(nextQuery, options) : options, 0, 1));
                }}
                onKeyDown={onListKeyDown}
                className="h-9 min-w-0 flex-1 bg-transparent font-mono text-[12px] text-fg placeholder:text-faint focus:outline-none"
              />
              <span className="shrink-0 font-mono text-[10px] text-faint" aria-live="polite">{items.length}/{options.length}</span>
            </label>
          ) : null}
          <ul
            ref={listRef}
            id={listId}
            role="listbox"
            tabIndex={-1}
            aria-labelledby={ariaLabelledBy}
            aria-label={ariaLabelledBy ? undefined : ariaLabel}
            aria-activedescendant={searchable ? undefined : activeDescendant}
            onKeyDown={searchable ? undefined : onListKeyDown}
            className="max-h-72 overflow-y-auto py-1 focus:outline-none"
          >
            {items.length ? items.map((option, index) => {
              const isSelected = option.value === value;
              const isActive = index === activeIndex;
              return (
                <li
                  key={option.value}
                  id={optionId(index)}
                  role="option"
                  aria-selected={isSelected}
                  aria-disabled={option.disabled || undefined}
                  onMouseDown={(event) => event.preventDefault()}
                  onMouseMove={() => !option.disabled && !isActive && setActiveIndex(index)}
                  onClick={() => choose(option)}
                  className={`flex cursor-pointer items-start gap-2 border-l-[3px] px-3 py-2 ${isActive ? 'border-accent bg-[var(--accent-soft)]' : 'border-transparent'} ${option.disabled ? 'cursor-not-allowed opacity-60' : ''}`}
                >
                  <span className="min-w-0 flex-1">
                    <span className={`flex min-w-0 items-center gap-2 text-[13px] ${isActive ? 'text-accent-ink' : 'text-fg'}`}>
                      <span className="truncate"><Highlighted segments={option.labelSegments} text={option.label} /></span>
                      {option.badge ? <span className="shrink-0 border border-border-2 px-1 font-mono text-[9px] uppercase tracking-[0.08em] text-muted">{option.badge}</span> : null}
                    </span>
                    {option.secondary ? <span className="block truncate font-mono text-[11px] text-muted"><Highlighted segments={option.secondarySegments} text={option.secondary} /></span> : null}
                    {option.disabled && option.note ? <span className="mt-0.5 block font-mono text-[11px] text-faint">{option.note}</span> : null}
                  </span>
                  {isSelected ? <Check className="mt-0.5 h-3.5 w-3.5 shrink-0 text-accent" strokeWidth={2.2} aria-hidden="true" /> : null}
                </li>
              );
            }) : <li className="px-3 py-3 font-mono text-[11px] text-faint" role="presentation">{emptyText}</li>}
          </ul>
        </div>
      ) : null}
    </div>
  );
}
