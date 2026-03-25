'use client';

import React from 'react';
import { GitMerge } from 'lucide-react';
import { CodeBlock } from '@/components/ui/CodeBlock';
import { Button } from '@/components/ui/Button';

export function KnowledgeCard({
  title,
  description,
  originMR,
  originDate,
  originAuthor,
  codeSnippet,
  confidence,
  onPromote,
  className = '',
  ...props
}) {
  return (
    <div
      className={`rounded-lg bg-surface-container p-5 ${className}`}
      {...props}
    >
      <div className="flex gap-6">
        {/* Origin column */}
        <div className="w-36 flex-shrink-0 space-y-1">
          <span className="text-[11px] font-semibold uppercase tracking-widest text-on-surface-faint font-body">
            ORIGIN
          </span>
          <div className="flex items-center gap-1.5">
            <span className="text-sm font-semibold text-on-surface font-body">{originMR}</span>
            <GitMerge className="w-3.5 h-3.5 text-on-surface-faint" />
          </div>
          {originDate && (
            <p className="text-xs text-on-surface-faint font-body">
              {originDate}
              {originAuthor && <> by {originAuthor}</>}
            </p>
          )}
        </div>

        {/* Content */}
        <div className="flex-1 space-y-3">
          <h4 className="text-sm font-semibold text-on-surface font-body flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-primary" />
            {title}
          </h4>
          {description && (
            <p className="text-sm text-on-surface-muted font-body">{description}</p>
          )}
          {codeSnippet && (
            <CodeBlock>{codeSnippet}</CodeBlock>
          )}
        </div>

        {/* Confidence + actions */}
        <div className="w-36 flex-shrink-0 text-right space-y-3">
          {confidence != null && (
            <div>
              <span className="text-[11px] font-semibold uppercase tracking-widest text-on-surface-faint font-body">
                CONFIDENCE
              </span>
              <p className="text-2xl font-bold font-headline text-tertiary">{confidence}%</p>
            </div>
          )}
          {onPromote && (
            <Button variant="outline" size="sm" onClick={onPromote}>
              Promote to Org
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
