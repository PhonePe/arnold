'use client';

import React from 'react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';

// Line tint by type (add/del/flag) — matches the Lattice .diff .ln scale.
function lineTint(type) {
  if (type === 'add') return 'bg-[color-mix(in_oklch,var(--ok)_10%,transparent)]';
  if (type === 'del') return 'bg-[color-mix(in_oklch,var(--bad)_9%,transparent)]';
  if (type === 'flag') return 'bg-[var(--accent-soft)]';
  return 'hover:bg-[var(--ink-wash)]';
}

function gutterTint(type) {
  if (type === 'add') return 'text-ok';
  if (type === 'del') return 'text-bad';
  return 'text-faint';
}

function DiffColumn({ title, lines, highlights }) {
  return (
    <div className="min-w-0">
      <div className="border-b border-border bg-surface px-4 py-2 font-mono text-[10px] uppercase tracking-[0.12em] text-faint">
        {title}
      </div>
      <div className="overflow-x-auto bg-surface-2">
        {lines.map((line, i) => {
          const type = line.type || (highlights.includes(line.number) ? 'flag' : undefined);
          return (
            <div key={i} className={`flex font-mono text-[12.5px] leading-[1.7] transition-colors ${lineTint(type)}`}>
              <span className={`w-11 flex-none select-none py-px pr-3 text-right ${gutterTint(type)}`}>
                {line.number}
              </span>
              <span className="whitespace-pre py-px pr-3 text-fg">{line.content}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// Lattice diff: hairline border, mono header, sharp corners. Side-by-side
// columns with add/del/flag line tints.
export function DiffViewer({
  filePath,
  fileStatus = 'modified',
  originalLines = [],
  modifiedLines = [],
  highlights = [],
  onViewBlame,
  onResolveAll,
  className = '',
  ...props
}) {
  return (
    <div
      className={`overflow-hidden rounded-none border border-border bg-surface-2 ${className}`}
      {...props}
    >
      {/* File header */}
      <div className="flex items-center justify-between gap-2.5 border-b border-border bg-surface px-3.5 py-2.5">
        <div className="flex items-center gap-2.5">
          <span className="font-mono text-[12.5px] font-semibold text-fg">{filePath}</span>
          <Badge variant="legacy">{fileStatus}</Badge>
        </div>
        <div className="flex items-center gap-2">
          {onViewBlame && (
            <Button variant="secondary" size="xs" onClick={onViewBlame}>
              View blame
            </Button>
          )}
          {onResolveAll && (
            <Button variant="primary" size="xs" onClick={onResolveAll}>
              Resolve all
            </Button>
          )}
        </div>
      </div>

      {/* Side-by-side columns (hairline divider between) */}
      <div className="grid grid-cols-2 divide-x divide-border">
        <DiffColumn title="Original" lines={originalLines} highlights={highlights} />
        <DiffColumn title="Analysis & fixes" lines={modifiedLines} highlights={highlights} />
      </div>
    </div>
  );
}
