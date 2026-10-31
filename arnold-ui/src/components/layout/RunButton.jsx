'use client';

import React, { useEffect, useRef } from 'react';
import { initLoader } from '@/lib/arnold-mark';

// The "Run Analysis" CTA — a flat vermilion accent panel with the engraved
// lattice grid and the animated "A" lettermark (full tip-over loop) as its
// glyph. The mark is 18px, left-aligned inside a 52×28 window so the bar has
// room to slide during the animation. The loop pauses while the sidebar is
// collapsed (matching the reference behaviour).
export function RunButton({ collapsed = false, onClick, className = '', ...props }) {
  const glyphRef = useRef(null);
  const loaderRef = useRef(null);

  // Mount the loader once.
  useEffect(() => {
    if (!glyphRef.current) return undefined;
    loaderRef.current = initLoader(glyphRef.current, { mode: 'full', size: 18 });
    return () => {
      loaderRef.current?.destroy?.();
      loaderRef.current = null;
    };
  }, []);

  // Pause/resume with the collapsed state.
  useEffect(() => {
    const loader = loaderRef.current;
    if (!loader) return;
    if (collapsed) loader.stop();
    else loader.play();
  }, [collapsed]);

  return (
    <button
      onClick={onClick}
      title="Run Analysis"
      className={`accent-panel flex items-center rounded-none font-semibold text-on-accent transition-[background] hover:brightness-95 active:translate-y-px ${
        collapsed ? 'justify-center px-0 py-[9px]' : 'justify-start gap-2 py-[9px] pl-1.5 pr-3.5'
      } ${className}`}
      {...props}
    >
      <span
        ref={glyphRef}
        aria-hidden="true"
        className="flex h-[28px] flex-none items-center justify-start overflow-hidden text-on-accent"
        style={{ width: collapsed ? 28 : 52 }}
      />
      {!collapsed && <span className="text-[15px]">Run Analysis</span>}
    </button>
  );
}
