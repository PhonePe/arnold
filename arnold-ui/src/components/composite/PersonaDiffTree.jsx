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

import { useEffect, useRef } from 'react';
import { Avatar } from '@/components/ui/Avatar';
import { GENERAL_PERSONA_KEY, personaInitials } from '@/lib/persona-grouping.mjs';

export function PersonaDiffTree({ groups, activeItemId, onNavigate, onShowOutdated, className = '', ...props }) {
  const activeRef = useRef(null);

  useEffect(() => {
    activeRef.current?.scrollIntoView({ block: 'nearest' });
  }, [activeItemId]);

  return <nav className={`max-h-52 overflow-y-auto border-b border-border bg-surface [contain:layout_paint] lg:max-h-[72vh] lg:border-b-0 lg:border-r ${className}`} aria-label="Findings by persona" {...props}>
    {groups.map((group) => <section key={group.key}>
      <button className="sticky top-0 z-10 flex w-full items-center gap-2 border-b border-border bg-surface-2 px-3 py-2.5 text-left hover:bg-[var(--ink-wash)]" onClick={() => group.items.length ? onNavigate(group.items[0]) : onShowOutdated?.(group.key)}>
        <Avatar size="sm" initials={personaInitials(group.name)} variant={group.key === GENERAL_PERSONA_KEY ? 'ai' : 'default'} alt={group.name} />
        <span className="min-w-0 flex-1 truncate text-sm font-semibold text-fg" title={group.name}>{group.name}</span>
        <span className="shrink-0 font-mono text-xs text-muted">{group.threadCount + group.outdatedThreads.length} 💬</span>
      </button>
      <div>{group.items.map((item) => {
        const active = item.id === activeItemId;
        const path = item.fileDiff.name.split('/');
        const name = path.pop();
        return <button key={item.id} ref={active ? activeRef : null} className={`flex w-full min-w-0 items-center gap-2 border-b border-l-2 border-b-border py-2 pl-[2.375rem] pr-3 text-left font-mono text-sm ${active ? 'border-l-accent bg-[var(--accent-soft)] text-fg' : 'border-l-transparent text-muted hover:bg-[var(--ink-wash)] hover:text-fg'}`} title={item.fileDiff.name} aria-current={active ? 'true' : undefined} onClick={() => onNavigate(item)}>
          <span className="flex min-w-0 flex-1 flex-col">
            <span className="truncate">{name}</span>
            {path.length ? <span className="truncate text-xs text-faint">{path.join('/')}</span> : null}
          </span>
          <span className="flex shrink-0 items-center gap-1.5 text-xs"><span className="text-ok">+{item.stats?.additions ?? 0}</span><span className="text-bad">−{item.stats?.deletions ?? 0}</span><span>{item.threads.length} 💬</span></span>
        </button>;
      })}{group.outdatedThreads.length ? <button className="flex w-full items-center gap-2 border-b border-l-2 border-b-border border-l-transparent py-2 pl-[2.375rem] pr-3 text-left font-mono text-sm text-muted hover:bg-[var(--ink-wash)] hover:text-fg" onClick={() => onShowOutdated?.(group.key)}>
        <span className="flex-1 truncate">Earlier versions</span>
        <span className="shrink-0 text-xs">{group.outdatedThreads.length} 💬</span>
      </button> : null}</div>
    </section>)}
    {!groups.length ? <p className="p-4 text-sm text-muted">No AI findings match these filters.</p> : null}
  </nav>;
}
