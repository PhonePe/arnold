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

/**
 * Seal — Arnold's vermilion brand mark.
 *
 * The "A" glyph painted with currentColor, sitting inside the accent seal
 * (vermilion background + engraved 8px grid + inner highlight). The glyph
 * inherits the seal's `--on-accent` color and flips with the theme.
 *
 * Sizing is driven by the `size` prop (pixels). Sharp corners by design.
 */
export function Seal({ size = 38, className = '', ...props }) {
  return (
    <span
      className={`seal ${className}`}
      style={{ width: size, height: size }}
      aria-label="Arnold"
      role="img"
      {...props}
    >
      <svg viewBox="-140 -162 280 324" fill="currentColor" aria-hidden="true">
        <path d="M-130 150 L-28 -150 L28 -150 L130 150 L72 150 L0 -61.8 L-72 150 Z" />
        <rect x="-78" y="5" width="208" height="50" />
      </svg>
    </span>
  );
}
