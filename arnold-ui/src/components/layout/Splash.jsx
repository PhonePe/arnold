'use client';

import React, { useEffect, useRef, useState } from 'react';
import { initHero } from '@/lib/arnold-mark';

function alreadySeen() {
  try {
    return sessionStorage.getItem('arnold.splash.seen') === '1';
  } catch (e) {
    return false;
  }
}

function prefersReducedMotion() {
  return typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/**
 * Splash — the "torn paper" boot reveal. Two vermilion halves split apart to
 * reveal the app, with the animated "A" hero mark in the center. The hero
 * animation runs for ~5s (its full tip-over → ">_" → stand-back-up cycle), then
 * the curtain tears away. Plays once per session. Honours prefers-reduced-motion
 * (short hold, no motion).
 *
 * `holdMs` is how long the curtain stays before tearing (default 5000ms to let
 * the full hero animation play). After tearing, it fades for 900ms then unmounts.
 */
export function Splash({ holdMs }) {
  const heroRef = useRef(null);
  // Lazy init avoids a synchronous setState inside the effect. On the server
  // this returns `true` (render nothing) to keep hydration stable.
  const [hidden, setHidden] = useState(() =>
    typeof window === 'undefined' ? true : alreadySeen(),
  );
  const [torn, setTorn] = useState(false);

  useEffect(() => {
    if (hidden) return undefined;

    const reduce = prefersReducedMotion();
    const hold = holdMs != null ? holdMs : reduce ? 600 : 5000;

    const hero = heroRef.current ? initHero(heroRef.current, { loop: false }) : null;

    const tearTimer = setTimeout(() => setTorn(true), hold);
    const hideTimer = setTimeout(() => {
      setHidden(true);
      try {
        sessionStorage.setItem('arnold.splash.seen', '1');
      } catch (e) {
        /* ignore */
      }
    }, hold + 900);

    return () => {
      clearTimeout(tearTimer);
      clearTimeout(hideTimer);
      hero?.destroy?.();
    };
  }, [hidden, holdMs]);

  if (hidden) return null;

  return (
    <div className={`splash ${torn ? 'torn' : ''}`} aria-hidden="true">
      <div className="splash-left" />
      <div className="splash-right" />
      <div className="splash-hero" ref={heroRef} role="img" aria-label="Arnold" />
    </div>
  );
}
