'use client';

import React from 'react';
import { Building2, Users, FolderKanban } from 'lucide-react';
import { RuleCard } from './RuleCard';

const levelConfig = {
  org: { icon: Building2, label: 'ORG-LEVEL' },
  group: { icon: Users, label: 'GROUP-LEVEL' },
  project: { icon: FolderKanban, label: 'PROJECT-LEVEL' },
};

export function RuleLevelCard({
  level = 'org',
  rules = [],
  highlighted = false,
  className = '',
  ...props
}) {
  const config = levelConfig[level] || levelConfig.org;
  const Icon = config.icon;

  return (
    <div
      className={`rounded-lg p-4 space-y-4 ${
        highlighted
          ? 'bg-surface-container-high ghost-border'
          : 'bg-surface-container'
      } ${className}`}
      {...props}
    >
      {/* Header */}
      <div className="flex items-center gap-2">
        <Icon className="w-5 h-5 text-on-surface-muted" />
        <span className="text-sm font-semibold font-headline text-on-surface">{config.label}</span>
      </div>

      {/* Rules */}
      <div className="space-y-3">
        {rules.map((rule, i) => (
          <RuleCard key={i} {...rule} />
        ))}
      </div>
    </div>
  );
}
