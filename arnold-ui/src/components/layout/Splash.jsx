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
 * The hero container is deliberately left empty in the server markup: `initHero`
 * builds the animated SVG and fades it in from frame 0. Rendering a static mark
 * first and swapping it out made the "A" pop in, blink to nothing, then fade
 * back — the exact opposite of the reference's clean fade-in.
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

    let hero = null;
    try {
      hero = heroRef.current ? initHero(heroRef.current, { loop: false }) : null;
    } catch (error) {
      // The mark is decorative; the dismissal timers still reveal the app.
    }

    const tearTimer = setTimeout(() => splash.classList.add('torn'), hold);
    const hideTimer = setTimeout(() => {
      splash.classList.add('hidden');
      document.documentElement.setAttribute('data-splash', 'hide');
    }, hold + 900);

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
      <div className="splash-hero" ref={heroRef} role="img" aria-label="Arnold" />
    </div>
  );
}
