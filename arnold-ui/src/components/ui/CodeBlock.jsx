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

// Code block: highest surface with a hairline border, IBM Plex Mono.
export function CodeBlock({
  children,
  className = '',
  ...props
}) {
  return (
    <div
      className={`overflow-x-auto rounded-none border border-border bg-surface-2 p-3 ${className}`}
      {...props}
    >
      <pre className="whitespace-pre font-mono text-[12.5px] leading-[1.65] text-fg">
        <code>{children}</code>
      </pre>
    </div>
  );
}
