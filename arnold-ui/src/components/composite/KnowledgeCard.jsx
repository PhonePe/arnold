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
import { GitMerge } from 'lucide-react';
import { CodeBlock } from '@/components/ui/CodeBlock';
import { Button } from '@/components/ui/Button';

// Lattice knowledge card: hairline border, mono origin/confidence columns, an
// accent dot on the title, and a Space Grotesk confidence figure.
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
      className={`rounded-none border border-border bg-surface p-5 ${className}`}
      {...props}
    >
      <div className="flex gap-6">
        {/* Origin column */}
        <div className="w-36 flex-none space-y-1">
          <span className="font-mono text-[10px] uppercase tracking-[0.12em] text-faint">
            Origin
          </span>
          <div className="flex items-center gap-1.5">
            <span className="font-mono text-[13px] font-semibold text-fg">{originMR}</span>
            <GitMerge className="h-3.5 w-3.5 text-faint" />
          </div>
          {originDate && (
            <p className="font-mono text-[11px] text-faint">
              {originDate}
              {originAuthor && <> · {originAuthor}</>}
            </p>
          )}
        </div>

        {/* Content */}
        <div className="flex-1 space-y-3">
          <h4 className="flex items-center gap-2 text-sm font-semibold text-fg">
            <span className="h-2 w-2 rounded-full bg-accent" />
            {title}
          </h4>
          {description && <p className="text-sm text-muted">{description}</p>}
          {codeSnippet && <CodeBlock>{codeSnippet}</CodeBlock>}
        </div>

        {/* Confidence + actions */}
        <div className="w-36 flex-none space-y-3 text-right">
          {confidence != null && (
            <div>
              <span className="font-mono text-[10px] uppercase tracking-[0.12em] text-faint">
                Confidence
              </span>
              <p className="font-display text-2xl font-bold text-tertiary-ink">{confidence}%</p>
            </div>
          )}
          {onPromote && (
            <Button variant="secondary" size="sm" onClick={onPromote}>
              Promote to Org
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
