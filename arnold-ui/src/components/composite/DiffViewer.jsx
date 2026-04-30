'use client';

import React from 'react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';

export function DiffViewer({
  filePath,
  fileStatus = 'MODIFIED',
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
      className={`rounded-lg bg-surface-container overflow-hidden ${className}`}
      {...props}
    >
      {/* File header */}
      <div className="flex items-center justify-between px-4 py-3 bg-surface-container-high">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-on-surface font-code">
            {filePath}
          </span>
          <Badge variant="info">{fileStatus}</Badge>
        </div>
        <div className="flex items-center gap-2">
          {onViewBlame && (
            <Button variant="outline" size="sm" onClick={onViewBlame}>
              VIEW BLAME
            </Button>
          )}
          {onResolveAll && (
            <Button variant="primary" size="sm" onClick={onResolveAll}>
              RESOLVE ALL
            </Button>
          )}
        </div>
      </div>

      {/* Column headers */}
      <div className="grid grid-cols-2">
        <div className="px-4 py-2 text-[11px] font-semibold uppercase tracking-widest text-on-surface-faint font-body bg-surface-container-low">
          ORIGINAL
        </div>
        <div className="px-4 py-2 text-[11px] font-semibold uppercase tracking-widest text-on-surface-faint font-body bg-surface-container-low">
          ANALYSIS &amp; FIXES
        </div>
      </div>

      {/* Diff content — no row lines, hover highlight per DESIGN.md */}
      <div className="grid grid-cols-2 font-code text-[0.875rem] leading-[1.6]">
        {/* Original */}
        <div className="bg-surface-container-lowest overflow-x-auto">
          {originalLines.map((line, i) => (
            <div key={i} className="flex hover:bg-surface-container-low transition-colors">
              <span className="w-12 flex-shrink-0 text-right pr-3 py-0.5 text-on-surface-faint select-none">
                {line.number}
              </span>
              <span className="py-0.5 pr-3 text-on-surface-variant whitespace-pre">
                {line.content}
              </span>
            </div>
          ))}
        </div>

        {/* Modified */}
        <div className="bg-surface-container-lowest overflow-x-auto">
          {modifiedLines.map((line, i) => {
            const isHighlighted = highlights.includes(line.number);
            return (
              <div
                key={i}
                className={`flex transition-colors ${
                  isHighlighted
                    ? 'bg-error-container/30'
                    : 'hover:bg-surface-container-low'
                }`}
              >
                <span className="w-12 flex-shrink-0 text-right pr-3 py-0.5 text-on-surface-faint select-none">
                  {line.number}
                </span>
                <span className="py-0.5 pr-3 text-on-surface-variant whitespace-pre">
                  {line.content}
                </span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
