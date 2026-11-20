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
import { StatusIndicator } from '@/components/ui/StatusIndicator';
import { Avatar } from '@/components/ui/Avatar';

const statusMap = {
  analyzing: { status: 'warning', label: 'Analysing' },
  ready: { status: 'accent', label: 'Review ready' },
  'issue-found': { status: 'error', label: 'Issue found' },
  'in-progress': { status: 'info', label: 'In progress' },
  complete: { status: 'success', label: 'Merged' },
  merged: { status: 'success', label: 'Merged' },
  'critical-alert': { status: 'error', label: 'Critical alert' },
  created: { status: 'neutral', label: 'Created' },
};

// MR row (.mr): hairline-bordered, sharp, a mono ID badge (vermilion
// when critical), title + mono meta, status, and an avatar stack.
export function MRListItem({
  branch,
  mrNumber,
  description,
  status = 'analyzing',
  project,
  critical = false,
  reviewers = [],
  meta,
  onClick,
  className = '',
  ...props
}) {
  const statusConfig = statusMap[status] || statusMap.analyzing;
  const metaText =
    meta || [project, description].filter(Boolean).join(' · ');

  return (
    <div
      className={`flex items-center gap-3.5 rounded-none border border-border bg-surface px-4 py-3 transition-[border-color,box-shadow] hover:border-border-2 hover:shadow-sm ${
        onClick ? 'cursor-pointer hover:border-accent' : ''
      } ${className}`}
      onClick={onClick}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      {...props}
    >
      {/* ID badge + state */}
      <div className="flex w-[120px] flex-none flex-col gap-2">
        {mrNumber && (
          <span
            className={`w-max rounded-none px-2 py-1 font-mono text-[12px] font-semibold ${
              critical ? 'bg-accent text-on-accent' : 'bg-fg text-surface'
            }`}
          >
            {mrNumber}
          </span>
        )}
        <StatusIndicator status={statusConfig.status} label={statusConfig.label} />
      </div>

      {/* Title + meta */}
      <div className="min-w-0 flex-1">
        <div className="truncate text-[14.5px] font-semibold text-fg">{branch}</div>
        {metaText && (
          <div className="mt-0.5 truncate font-mono text-[11.5px] text-muted">{metaText}</div>
        )}
      </div>

      {/* Reviewers */}
      {reviewers.length > 0 && (
        <div className="flex flex-none">
          {reviewers.map((r, i) => (
            <Avatar
              key={i}
              initials={r}
              size="md"
              className={`border-2 border-surface ${i > 0 ? '-ml-[7px]' : ''}`}
            />
          ))}
        </div>
      )}
    </div>
  );
}
