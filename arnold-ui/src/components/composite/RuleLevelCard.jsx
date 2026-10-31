'use client';

import React from 'react';
import { Building2, Users, FolderKanban } from 'lucide-react';
import { RuleCard } from './RuleCard';

const levelConfig = {
  org: { icon: Building2, label: 'Org-level' },
  group: { icon: Users, label: 'Group-level' },
  project: { icon: FolderKanban, label: 'Project-level' },
};

// Lattice rule-level group: a labelled section on the raised surface. When
// highlighted, it picks up the accent border.
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
      className={`space-y-4 rounded-none border bg-surface p-4 ${
        highlighted ? 'border-accent' : 'border-border'
      } ${className}`}
      {...props}
    >
      {/* Header */}
      <div className="flex items-center gap-2">
        <Icon className="h-[18px] w-[18px] text-muted" strokeWidth={1.8} />
        <span className="font-display text-sm font-bold tracking-tight text-fg">
          {config.label}
        </span>
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
