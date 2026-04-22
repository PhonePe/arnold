'use client';

import React from 'react';

export function CodeBlock({
  children,
  className = '',
  ...props
}) {
  return (
    <div
      className={`rounded-sm bg-surface-container-highest p-3 overflow-x-auto ${className}`}
      {...props}
    >
      <pre className="font-code text-[0.875rem] leading-[1.6] text-on-surface-variant whitespace-pre">
        <code>{children}</code>
      </pre>
    </div>
  );
}
