'use client';

import React from 'react';

// Lattice code block: highest surface with a hairline border, IBM Plex Mono.
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
