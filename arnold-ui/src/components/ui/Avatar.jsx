'use client';

import React from 'react';

const sizeMap = {
  xs: 'w-[18px] h-[18px] text-[8px]',
  sm: 'w-[22px] h-[22px] text-[9px]',
  md: 'w-[30px] h-[30px] text-[11px]',
  lg: 'w-[38px] h-[38px] text-[13px]',
};

// Lattice avatars: circular, charcoal-ink background, IBM Plex Mono initials.
// `variant="ai"` paints it in the vermilion signal.
export function Avatar({
  src,
  alt = '',
  initials,
  size = 'md',
  variant = 'default',
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

  const tone =
    variant === 'ai'
      ? 'bg-accent text-on-accent'
      : 'bg-fg text-surface';

  return (
    <span
      className={`inline-flex flex-none items-center justify-center rounded-full font-mono font-bold ${tone} ${sizeClasses} ${className}`}
      aria-label={alt}
      {...props}
    >
      {initials || '?'}
    </span>
  );
}
