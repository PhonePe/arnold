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

import { useEffect, useMemo, useRef, useState } from 'react';
import { ChevronDown, ChevronRight, Folder, FolderOpen, List, ListTree } from 'lucide-react';
import { buildFileTree } from '@/lib/file-tree.mjs';

const padding = ['pl-2', 'pl-4', 'pl-6', 'pl-8', 'pl-10', 'pl-12'];

export function DiffFileTree({ items, viewed, activeItemId, onNavigate, onToggleViewed, onToggleCollapsed, className = '', ...props }) {
  const tree = useMemo(() => buildFileTree(items), [items]);
  const [collapsed, setCollapsed] = useState(new Set());
  const [view, setView] = useState('tree');
  const activeRef = useRef(null);

  useEffect(() => {
    activeRef.current?.scrollIntoView({ block: 'nearest' });
  }, [activeItemId]);

  function toggleDirectory(path) {
    setCollapsed((current) => {
      const next = new Set(current);
      if (next.has(path)) next.delete(path); else next.add(path);
      return next;
    });
  }

  function fileRow(item, label, paddingClass = '') {
    return <div key={item.id} ref={activeItemId === item.id ? activeRef : null} className={`grid grid-cols-[auto_1fr_auto] border-b border-border ${viewed.has(item.fileDiff.name) ? 'opacity-60' : ''} ${activeItemId === item.id ? 'bg-[var(--ink-wash)]' : ''}`}>
      <label className="grid w-8 place-items-center"><input type="checkbox" checked={viewed.has(item.fileDiff.name)} onChange={() => onToggleViewed(item.fileDiff.name)} aria-label={`Mark ${item.fileDiff.name} viewed`} /></label>
      <button className={`flex min-w-0 items-center gap-2 py-2 pr-1 text-left font-mono text-sm text-muted hover:text-fg ${paddingClass}`} title={item.fileDiff.name} onClick={() => onNavigate(item)}><span className="min-w-0 flex-1 truncate">{label}</span><span className="flex shrink-0 items-center gap-1.5"><span className="text-ok">+{item.stats.additions}</span><span className="text-bad">−{item.stats.deletions}</span>{item.threads.length ? <span>{item.threads.length} 💬</span> : null}</span></button>
      <button className="grid w-9 place-items-center border-l border-border text-muted hover:text-fg" aria-label={`${item.collapsed ? 'Expand' : 'Collapse'} ${item.fileDiff.name}`} onClick={() => onToggleCollapsed(item.id)}>{item.collapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}</button>
    </div>;
  }

  function render(nodes, depth = 0) {
    return nodes.map((node) => {
      if (node.type === 'directory') {
        const closed = collapsed.has(node.path);
        return <div key={node.path}>
          <button className={`flex w-full items-center gap-1 border-b border-border py-2 pr-2 text-left font-mono text-sm text-muted hover:bg-[var(--ink-wash)] hover:text-fg ${padding[Math.min(depth, padding.length - 1)]}`} aria-expanded={!closed} onClick={() => toggleDirectory(node.path)}>
            {closed ? <ChevronRight className="h-3.5 w-3.5 shrink-0" /> : <ChevronDown className="h-3.5 w-3.5 shrink-0" />}
            {closed ? <Folder className="h-3.5 w-3.5 shrink-0" /> : <FolderOpen className="h-3.5 w-3.5 shrink-0" />}
            <span className="truncate">{node.name}</span>
          </button>
          {!closed ? render(node.children, depth + 1) : null}
        </div>;
      }
      const { item } = node;
      return fileRow(item, node.name, padding[Math.min(depth, padding.length - 1)]);
    });
  }

  return <nav className={`max-h-52 overflow-y-auto border-b border-border bg-surface lg:max-h-[72vh] lg:border-b-0 lg:border-r ${className}`} aria-label="Changed files" {...props}>
    <div className="sticky top-0 z-10 flex justify-end border-b border-border bg-surface p-2" role="group" aria-label="File index view">
      <button type="button" className={`grid h-7 w-8 place-items-center border border-border-2 ${view === 'tree' ? 'bg-accent text-on-accent' : 'bg-surface-2 text-muted hover:text-fg'}`} title="Tree view" aria-label="Tree view" aria-pressed={view === 'tree'} onClick={() => setView('tree')}><ListTree className="h-4 w-4" /></button>
      <button type="button" className={`-ml-px grid h-7 w-8 place-items-center border border-border-2 ${view === 'list' ? 'bg-accent text-on-accent' : 'bg-surface-2 text-muted hover:text-fg'}`} title="List view" aria-label="List view" aria-pressed={view === 'list'} onClick={() => setView('list')}><List className="h-4 w-4" /></button>
    </div>
    {view === 'tree' ? render(tree) : items.map((item) => fileRow(item, item.fileDiff.name, 'pl-2'))}
    {!items.length ? <p className="p-4 text-sm text-muted">No files match these filters.</p> : null}
  </nav>;
}
