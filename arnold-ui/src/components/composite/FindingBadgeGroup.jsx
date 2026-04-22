'use client';

import React from 'react';
import { Badge } from '@/components/ui/Badge';

const variantMap = {
  security: 'security',
  logic: 'logic',
  style: 'style',
  nit: 'nit',
  runtime: 'runtime',
};

export function FindingBadgeGroup({
  findings = [],
  className = '',
  ...props
}) {
  return (
    <div className={`flex flex-wrap gap-2 ${className}`} {...props}>
      {findings.map((finding, i) => (
        <Badge
          key={i}
          variant={variantMap[finding.type] || 'default'}
        >
          {finding.type} ({finding.count})
        </Badge>
      ))}
    </div>
  );
}
