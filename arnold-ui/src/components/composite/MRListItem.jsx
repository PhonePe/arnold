'use client';

import React from 'react';
import { GitMerge, ChevronRight } from 'lucide-react';
import { StatusIndicator } from '@/components/ui/StatusIndicator';
import { Avatar } from '@/components/ui/Avatar';

const statusMap = {
  analyzing: { status: 'warning', label: 'ANALYZING' },
  ready: { status: 'success', label: 'READY' },
  'issue-found': { status: 'error', label: 'ISSUE FOUND' },
  'in-progress': { status: 'info', label: 'IN PROGRESS' },
  complete: { status: 'success', label: 'COMPLETE' },
  'critical-alert': { status: 'error', label: 'CRITICAL ALERT' },
};

export function MRListItem({
  branch,
  mrNumber,
  description,
  status = 'analyzing',
  project,
  reviewers = [],
  progress,
  onClick,
  className = '',
  ...props
}) {
  const statusConfig = statusMap[status] || statusMap.analyzing;

  return (
    <div
      className={`flex items-center gap-4 p-4 rounded-lg bg-surface-container hover:bg-surface-container-high transition-colors cursor-pointer ${className}`}
      onClick={onClick}
      role="button"
      tabIndex={0}
      {...props}
    >
      {/* Left accent */}
      <div className="w-1 h-10 rounded-full bg-primary flex-shrink-0" />

      {/* Icon */}
      <GitMerge className="w-5 h-5 text-on-surface-faint flex-shrink-0" />

      {/* Content */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium font-body text-on-surface truncate">
            {branch}
          </span>
          {mrNumber && (
            <span className="text-xs text-on-surface-faint font-body">{mrNumber}</span>
          )}
        </div>
        <p className="text-xs text-on-surface-muted font-body truncate mt-0.5">
          {project && <span className="text-on-surface-faint">{project} &middot; </span>}
          {description}
        </p>
      </div>

      {/* Status */}
      <StatusIndicator
        status={statusConfig.status}
        label={statusConfig.label}
      />

      {/* Reviewers */}
      {reviewers.length > 0 && (
        <div className="flex -space-x-1.5">
          {reviewers.map((r, i) => (
            <Avatar key={i} initials={r} size="sm" />
          ))}
        </div>
      )}

      {/* Chevron */}
      <ChevronRight className="w-4 h-4 text-on-surface-faint flex-shrink-0" />
    </div>
  );
}
