'use client';

/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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
