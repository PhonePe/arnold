'use client';

import React, { useEffect, useRef } from 'react';
import { initHero } from '@/lib/arnold-mark';

function prefersReducedMotion() {
  return typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/**
 * Splash — the "torn paper" boot reveal. Two vermilion halves split apart to
 * reveal the app, with the animated "A" hero mark in the center. The hero
 * animation runs for ~5s (its full tip-over → ">_" → stand-back-up cycle), then
 * the curtain tears away. Plays for a new browser profile and then once every
 * seven days. Honours prefers-reduced-motion (short hold, no motion).
 *
 * `holdMs` is how long the curtain stays before tearing (default 5500ms: one
 * full hero cycle plus the reference's 500ms beat). It hides 900ms later.
 */
export function Splash({ holdMs }) {
  const splashRef = useRef(null);
  const heroRef = useRef(null);

  useEffect(() => {
    const splash = splashRef.current;
    if (!splash || document.documentElement.getAttribute('data-splash') !== 'show') {
      return undefined;
    }

    const reduce = prefersReducedMotion();
    const hold = holdMs != null ? holdMs : reduce ? 600 : 5500;
    const tearTimer = setTimeout(() => splash.classList.add('torn'), hold);
    const hideTimer = setTimeout(() => {
      splash.classList.add('hidden');
      document.documentElement.setAttribute('data-splash', 'hide');
    }, hold + 900);

    let hero = null;
    try {
      hero = heroRef.current ? initHero(heroRef.current, { loop: false }) : null;
      heroRef.current?.querySelector('[data-splash-fallback]')?.remove();
    } catch (error) {
      // Keep the static mark visible; the dismissal timers still reveal the app.
    }

    return () => {
      clearTimeout(tearTimer);
      clearTimeout(hideTimer);
      hero?.destroy?.();
    };
  }, [holdMs]);

  return (
    <div className="splash" ref={splashRef} aria-hidden="true">
      <div className="splash-left" />
      <div className="splash-right" />
      <div className="splash-hero" ref={heroRef} role="img" aria-label="Arnold">
        <svg
          data-splash-fallback
          viewBox="0 0 1040 560"
          width="100%"
          height="100%"
          aria-hidden="true"
        >
          <g
            fill="currentColor"
            transform="translate(520 280) scale(1) translate(-264 0)"
          >
            <path d="M -130 150 L -28 -150 L 28 -150 L 130 150 L 72 150 L 0 -61.8 L -72 150 Z" />
            <rect x="-78" y="5" width="208" height="50" />
          </g>
        </svg>
      </div>
    </div>
  );
}
