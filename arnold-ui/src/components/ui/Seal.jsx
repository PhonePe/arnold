'use client';

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
