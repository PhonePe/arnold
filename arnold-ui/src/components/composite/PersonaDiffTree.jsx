'use client';

import { useEffect, useRef } from 'react';
import { Avatar } from '@/components/ui/Avatar';
import { GENERAL_PERSONA_KEY, personaInitials } from '@/lib/persona-grouping.mjs';

export function PersonaDiffTree({ groups, activeItemId, onNavigate, className = '', ...props }) {
  const activeRef = useRef(null);

  useEffect(() => {
    activeRef.current?.scrollIntoView({ block: 'nearest' });
  }, [activeItemId]);

  return <nav className={`max-h-52 overflow-y-auto border-b border-border bg-surface [contain:layout_paint] lg:max-h-[72vh] lg:border-b-0 lg:border-r ${className}`} aria-label="Findings by persona" {...props}>
    {groups.map((group) => <section key={group.key}>
      <button className="flex w-full items-center gap-2 border-b border-border bg-surface-2 px-3 py-3 text-left hover:bg-[var(--ink-wash)]" onClick={() => onNavigate(group.items[0])}>
        <Avatar size="sm" initials={personaInitials(group.name)} variant={group.key === GENERAL_PERSONA_KEY ? 'ai' : 'default'} alt={group.name} />
        <span className="min-w-0 flex-1 truncate text-sm font-semibold text-fg" title={group.name}>{group.name}</span>
        <span className="shrink-0 font-mono text-xs text-accent-ink">{group.threadCount}</span>
      </button>
      <div className="ml-[21px] border-l border-border">{group.items.map((item) => {
        const active = item.id === activeItemId;
        return <button key={item.id} ref={active ? activeRef : null} className={`flex w-full min-w-0 items-center gap-2 border-b border-border py-2 pl-3 pr-3 text-left font-mono text-sm ${active ? 'bg-[var(--accent-soft)]' : 'hover:bg-[var(--ink-wash)]'}`} title={item.fileDiff.name} aria-current={active ? 'true' : undefined} onClick={() => onNavigate(item)}>
          <span className="min-w-0 flex-1 truncate font-semibold text-fg">{item.fileDiff.name.split('/').pop()}</span>
          <span className="shrink-0 text-xs text-muted">{item.threads.length}</span>
        </button>;
      })}</div>
    </section>)}
    {!groups.length ? <p className="p-4 text-sm text-muted">No AI findings match these filters.</p> : null}
  </nav>;
}
