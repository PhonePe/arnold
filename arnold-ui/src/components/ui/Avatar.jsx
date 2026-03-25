'use client';

import React from 'react';

const sizeMap = {
  sm: 'w-7 h-7 text-[10px]',
  md: 'w-9 h-9 text-xs',
  lg: 'w-11 h-11 text-sm',
};

export function Avatar({
  src,
  alt = '',
  initials,
  size = 'md',
  className = '',
  ...props
}) {
  const sizeClasses = sizeMap[size] || sizeMap.md;

  if (src) {
    return (
      <img
        src={src}
        alt={alt}
        className={`rounded-full object-cover ${sizeClasses} ${className}`}
        {...props}
      />
    );
  }

  return (
    <span
      className={`inline-flex items-center justify-center rounded-full bg-surface-container-high font-medium text-on-surface-muted ${sizeClasses} ${className}`}
      aria-label={alt}
      {...props}
    >
      {initials || '?'}
    </span>
  );
}
