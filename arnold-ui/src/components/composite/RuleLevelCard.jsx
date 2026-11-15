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
