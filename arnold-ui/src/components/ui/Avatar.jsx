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
import Image from 'next/image';

const sizeMap = {
  xs: 'w-[18px] h-[18px] text-[8px]',
  sm: 'w-[22px] h-[22px] text-[9px]',
  md: 'w-[30px] h-[30px] text-[11px]',
  lg: 'w-[38px] h-[38px] text-[13px]',
};

const pixelSizeMap = { xs: 18, sm: 22, md: 30, lg: 38 };

// Avatars: circular, charcoal-ink background, IBM Plex Mono initials.
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
      <Image
        src={src}
        alt={alt}
        width={pixelSizeMap[size] || pixelSizeMap.md}
        height={pixelSizeMap[size] || pixelSizeMap.md}
        unoptimized
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
