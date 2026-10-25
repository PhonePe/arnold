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

/*
 * Arnold animated lettermark — vector ("A") animations as a small ES module.
 *
 * Ports the original arnold-loader.js + arnold-hero.js. The "A" is drawn with
 * `currentColor`, so set the host element's text color and it is theme-aware
 * for free and crisp at any size. Honours `prefers-reduced-motion`.
 *
 *   import { initLoader, initHero } from '@/lib/arnold-mark';
 *   const loader = initLoader(el, { mode: 'pendulum', size: 28 });
 *   const hero   = initHero(el, { loop: false, shadow: false });
 *
 * Both return a handle: { play, stop?, destroy }.
 */

const NS = 'http://www.w3.org/2000/svg';
const FPS = 30;

// Shared geometry (matches arnold-mark.svg).
const CHEVRON = 'M -130 150 L -28 -150 L 28 -150 L 130 150 L 72 150 L 0 -61.8 L -72 150 Z';
const FOOT = [130, 150];
const BAR_BOTTOM = [250, 150];

// ── math kit (mirrors Remotion's interpolate / Easing / spring) ──────────────
const clamp01 = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);
const easeInQuad = (t) => t * t;
const easeOutQuad = (t) => 1 - (1 - t) * (1 - t);

function interp(x, x0, x1, y0, y1, ease) {
  let t = clamp01((x - x0) / (x1 - x0));
  if (ease) t = ease(t);
  return y0 + t * (y1 - y0);
}
const lerp = (s, a, b) => a + s * (b - a);
// Trim per-frame transform strings: sub-thousandth precision is invisible at
// any realistic render size but doubles the string the SVG parser chews on.
const r3 = (n) => Math.round(n * 1000) / 1000;

function ricochet(t, peak, dur, e, hops) {
  if (t <= 0) return 0;
  let start = 0;
  let p = peak;
  let d = dur;
  for (let i = 0; i < hops; i++) {
    if (t < start + d) {
      const u = (t - start) / d;
      return p * 4 * u * (1 - u);
    }
    start += d;
    p *= e * e;
    d *= e;
  }
  return 0;
}

function spring(frame, fps, cfg) {
  const { stiffness, damping, mass } = cfg;
  const t = frame / fps;
  if (t <= 0) return 0;
  const zeta = damping / (2 * Math.sqrt(stiffness * mass));
  const w0 = Math.sqrt(stiffness / mass);
  const x0 = -1;
  if (zeta < 1) {
    const w1 = w0 * Math.sqrt(1 - zeta * zeta);
    const env = Math.exp(-zeta * w0 * t);
    return 1 + env * (x0 * Math.cos(w1 * t) + ((zeta * w0 * x0) / w1) * Math.sin(w1 * t));
  }
  const env2 = Math.exp(-w0 * t);
  return 1 + env2 * (x0 + w0 * x0 * t);
}

function prefersReducedMotion() {
  return typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
}

// ════════════════════════════════════════════════════════════════════════════
// Loader — compact looping indicators
//   mode 'full'     the full hero animation (tip-over → ">_" → recover). 5s loop.
//   mode 'pendulum' the A rocks ±15° around its base. Calm metronome. 1.5s loop.
// ════════════════════════════════════════════════════════════════════════════

const LOADER_VIEWBOX = '-140 -162 280 324';
const LOADER_BAR = { x: -78, y: -1, w: 208, h: 58 };

// timeline shared by the 'full' loader mode and the hero (in frames @ 30fps)
const TL = {
  DUR: 150,
  F1S: 12,
  A_FALL: 22,
  F2S: 48,
  L_FALL: 14,
  L_IMPACT: 62,
  SLIDE: 200,
  SLIDE_DUR: 24,
  F4S: 116,
  STAGE1: 15,
};

const PENDULUM_DUR = 45;

function pendulumValues(frame) {
  const angle = 15 * Math.sin((frame / PENDULUM_DUR) * 2 * Math.PI);
  return { chev: angle, barA1: angle, barA2: 0, offX: 0, hop: 0 };
}

// The full tip-over → ">_" → stand-back-up cycle (shared by hero + loader).
function tipOverValues(frame) {
  let fall;
  if (frame <= TL.F1S) fall = 0;
  else if (frame - TL.F1S < TL.A_FALL) fall = interp(frame - TL.F1S, 0, TL.A_FALL, 0, 90, easeInQuad);
  else fall = 90 - ricochet(frame - TL.F1S - TL.A_FALL, 7, 8, 0.45, 2);

  let barFall;
  if (frame < TL.F2S) barFall = 0;
  else if (frame - TL.F2S < TL.L_FALL) barFall = interp(frame - TL.F2S, 0, TL.L_FALL, 0, 90, easeInQuad);
  else barFall = 90;

  const tSkid = frame - TL.L_IMPACT;
  let hop = ricochet(tSkid, 26, 11, 0.6, 4);
  let slide = interp(tSkid, 0, TL.SLIDE_DUR, 0, TL.SLIDE, easeOutQuad);

  let chev = fall;
  let barA1 = fall;
  let barA2 = barFall;
  let offX = slide;

  if (frame >= TL.F4S) {
    hop = 0;
    if (frame < TL.F4S + TL.STAGE1) {
      const s1 = spring(frame - TL.F4S, FPS, { damping: 16, mass: 0.9, stiffness: 120 });
      barA2 = lerp(s1, 90, 0);
      offX = Math.max(0, lerp(s1, TL.SLIDE, 0));
      chev = 90;
      barA1 = 90;
    } else {
      barA2 = 0;
      offX = 0;
      const s2 = spring(frame - (TL.F4S + TL.STAGE1), FPS, { damping: 15, mass: 1, stiffness: 95 });
      chev = lerp(s2, 90, 0);
      barA1 = chev;
    }
  }

  return { chev, barA1, barA2, offX, hop };
}

const LOADER_MODES = {
  full: { dur: TL.DUR, values: tipOverValues },
  pendulum: { dur: PENDULUM_DUR, values: pendulumValues },
};

let loaderUid = 0;

function buildLoader(container, size) {
  const uid = `al-${++loaderUid}`;
  const markup =
    `<svg xmlns="${NS}" viewBox="${LOADER_VIEWBOX}" width="${size}" height="${size}" ` +
    `preserveAspectRatio="xMidYMid meet" aria-hidden="true" role="img" style="display:block;overflow:visible">` +
    `<g id="${uid}-group" fill="currentColor">` +
    `<path id="${uid}-chev" d="${CHEVRON}"/>` +
    `<rect id="${uid}-bar" x="${LOADER_BAR.x}" y="${LOADER_BAR.y}" width="${LOADER_BAR.w}" height="${LOADER_BAR.h}"/>` +
    `</g></svg>`;
  const doc = new DOMParser().parseFromString(markup, 'image/svg+xml');
  const svg = container.ownerDocument.importNode(doc.documentElement, true);
  container.appendChild(svg);
  return {
    svg,
    chev: svg.querySelector(`#${uid}-chev`),
    bar: svg.querySelector(`#${uid}-bar`),
  };
}

function renderMark(els, v) {
  els.chev.setAttribute('transform', `rotate(${r3(v.chev)} ${FOOT[0]} ${FOOT[1]})`);
  els.bar.setAttribute(
    'transform',
    `translate(${r3(v.offX)} ${r3(-v.hop)}) rotate(${r3(v.barA2)} ${BAR_BOTTOM[0]} ${BAR_BOTTOM[1]}) rotate(${r3(v.barA1)} ${FOOT[0]} ${FOOT[1]})`,
  );
}

export function initLoader(container, opts = {}) {
  if (!container) return { play() {}, stop() {}, destroy() {} };
  const size = opts.size || 18;
  const mode = LOADER_MODES[opts.mode] || LOADER_MODES.full;
  const els = buildLoader(container, size);
  const rest = { chev: 0, barA1: 0, barA2: 0, offX: 0, hop: 0 };

  if (prefersReducedMotion()) {
    renderMark(els, rest);
    return { play() {}, stop() {}, destroy: () => els.svg.remove() };
  }

  let raf = null;
  let startTs = null;
  let running = false;

  function tick(ts) {
    if (startTs == null) startTs = ts;
    const frame = (((ts - startTs) / 1000) * FPS) % mode.dur;
    renderMark(els, mode.values(frame));
    if (running) raf = requestAnimationFrame(tick);
  }

  function play() {
    if (running) return;
    running = true;
    startTs = null;
    raf = requestAnimationFrame(tick);
  }
  function stop() {
    running = false;
    if (raf) cancelAnimationFrame(raf);
    raf = null;
    renderMark(els, rest);
  }
  function destroy() {
    stop();
    els.svg.remove();
  }

  renderMark(els, rest);
  play();
  return { play, stop, destroy };
}

// ════════════════════════════════════════════════════════════════════════════
// Hero — the full tip-over → ">_" → stand-back-up reveal (used by the splash)
//
// Carries a soft drop shadow by default (as the reference mark does): it gives
// the tip-over some weight and softens the edges of the rotating chevron, which
// otherwise shimmers at small render sizes. Pass `{ shadow: false }` to drop it.
// ════════════════════════════════════════════════════════════════════════════

const HERO_DURATION = 150;
const HERO_W = 1040;
const HERO_H = 560;
const HERO_MOTION_CX = 264;
const HERO_BAR = { x: -78, y: 5, w: 208, h: 50 };

const F1S = 12;
const A_FALL = 22;
const F2S = 48;
const L_FALL = 14;
const L_IMPACT = F2S + L_FALL;
const SLIDE = 200;
const SLIDE_DUR = 24;
const F4S = 116;
const STAGE1 = 15;

function heroValues(frame) {
  let fall;
  if (frame <= F1S) fall = 0;
  else if (frame - F1S < A_FALL) fall = interp(frame - F1S, 0, A_FALL, 0, 90, easeInQuad);
  else fall = 90 - ricochet(frame - F1S - A_FALL, 7, 8, 0.45, 2);

  let barFall;
  if (frame < F2S) barFall = 0;
  else if (frame - F2S < L_FALL) barFall = interp(frame - F2S, 0, L_FALL, 0, 90, easeInQuad);
  else barFall = 90;

  const tSkid = frame - L_IMPACT;
  let hop = ricochet(tSkid, 26, 11, 0.6, 4);
  let slide = interp(tSkid, 0, SLIDE_DUR, 0, SLIDE, easeOutQuad);

  let chev = fall;
  let barA1 = fall;
  let barA2 = barFall;
  let offX = slide;

  if (frame >= F4S) {
    hop = 0;
    if (frame < F4S + STAGE1) {
      const s1 = spring(frame - F4S, FPS, { damping: 16, mass: 0.9, stiffness: 120 });
      barA2 = lerp(s1, 90, 0);
      offX = Math.max(0, lerp(s1, SLIDE, 0));
      chev = 90;
      barA1 = 90;
    } else {
      barA2 = 0;
      offX = 0;
      const s2 = spring(frame - (F4S + STAGE1), FPS, { damping: 15, mass: 1, stiffness: 95 });
      chev = lerp(s2, 90, 0);
      barA1 = chev;
    }
  }

  return { chev, barA1, barA2, offX, hop, opacity: interp(frame, 0, 4, 0, 1) };
}

let heroUid = 0;

function buildHero(container, withShadow) {
  const uid = `ah-${++heroUid}`;
  const defs = withShadow
    ? `<defs><filter id="${uid}-shadow" x="-40%" y="-40%" width="180%" height="180%">` +
      `<feDropShadow dx="0" dy="10" stdDeviation="10" flood-color="#000" flood-opacity="0.18"/>` +
      `</filter></defs>`
    : '';
  const markup =
    `<svg xmlns="${NS}" viewBox="0 0 ${HERO_W} ${HERO_H}" preserveAspectRatio="xMidYMid meet" ` +
    `width="100%" height="100%" aria-hidden="true" style="display:block">` +
    defs +
    `<g id="${uid}-group" fill="currentColor"${withShadow ? ` filter="url(#${uid}-shadow)"` : ''} ` +
    `transform="translate(${HERO_W / 2} ${HERO_H / 2}) scale(1) translate(${-HERO_MOTION_CX} 0)">` +
    `<path id="${uid}-chev" d="${CHEVRON}"/>` +
    `<rect id="${uid}-bar" x="${HERO_BAR.x}" y="${HERO_BAR.y}" width="${HERO_BAR.w}" height="${HERO_BAR.h}"/>` +
    `</g></svg>`;
  const doc = new DOMParser().parseFromString(markup, 'image/svg+xml');
  const svg = container.ownerDocument.importNode(doc.documentElement, true);
  container.appendChild(svg);
  return {
    svg,
    group: svg.querySelector(`#${uid}-group`),
    chev: svg.querySelector(`#${uid}-chev`),
    bar: svg.querySelector(`#${uid}-bar`),
  };
}

function renderHero(els, frame, fade) {
  const v = heroValues(frame);
  renderMark(els, v);
  const opacity = fade === false ? 1 : v.opacity;
  if (els.opacity !== opacity) {
    els.group.setAttribute('opacity', opacity);
    els.opacity = opacity;
  }
}

export function initHero(container, opts = {}) {
  if (!container) return { play() {}, destroy() {} };
  const loop = opts.loop != null ? opts.loop : true;
  const withShadow = opts.shadow !== false;
  const els = buildHero(container, withShadow);

  if (prefersReducedMotion()) {
    renderHero(els, HERO_DURATION - 1, false);
    return { play() {}, destroy: () => els.svg.remove() };
  }

  let raf = null;
  let startTs = null;
  let firstCycle = true;

  function tick(ts) {
    if (startTs == null) startTs = ts;
    let frame = ((ts - startTs) / 1000) * FPS;
    if (frame >= HERO_DURATION) {
      if (loop) {
        startTs = ts;
        frame = 0;
        firstCycle = false;
      } else {
        renderHero(els, HERO_DURATION - 1, firstCycle);
        raf = null;
        return;
      }
    }
    renderHero(els, frame, firstCycle);
    raf = requestAnimationFrame(tick);
  }
  function play() {
    if (raf) cancelAnimationFrame(raf);
    startTs = null;
    firstCycle = true;
    raf = requestAnimationFrame(tick);
  }
  function destroy() {
    if (raf) cancelAnimationFrame(raf);
    raf = null;
    els.svg.remove();
  }

  renderHero(els, 0);
  play();
  return { play, destroy };
}
