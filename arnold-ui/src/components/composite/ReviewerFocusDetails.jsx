'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/Button';

const COLLAPSED_LENGTH = 220;

export function ReviewerFocusDetails({ title, explanation, className = '', ...props }) {
  const [expanded, setExpanded] = useState(false);
  const collapsible = `${title}: ${explanation}`.length > COLLAPSED_LENGTH;

  return <div className={className} {...props}>
    <p className={`text-sm leading-6 text-muted ${collapsible && !expanded ? 'line-clamp-3' : ''}`}>
      <span className="font-semibold text-fg">{title}:</span> {explanation}
    </p>
    {collapsible ? <Button className="mt-1 !h-6 !px-0 !text-xs" size="xs" variant="link" aria-expanded={expanded} onClick={() => setExpanded((value) => !value)}>{expanded ? 'Show less' : 'Show more'}</Button> : null}
  </div>;
}
