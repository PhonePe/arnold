/*
 * Arnold animated mark — vector (SVG) version of the hero animation.
 *
 * Drop-in: give it a styled container and it builds + animates the A inside.
 *
 *   <div class="arnold-hero" data-arnold-hero></div>
 *   <script src="arnold-hero.js"></script>
 *
 * The A is drawn with `currentColor`, so set the container's `color` to your
 * surface-2 (and its background to your accent) — see arnold-hero.html. It is
 * theme-aware for free and crisp at any size.
 *
 * Options via data-attributes (or ArnoldHero.init(el, opts)):
 *   data-loop="false"         play once (default: loop forever)
 *   data-replay="hover|click" restart on hover / click (default: none)
 *   data-shadow="false"       disable the soft drop shadow
 *
 * Honours `prefers-reduced-motion` (shows the resting A, no motion).
 */
(function () {
  "use strict";

  var FPS = 30,
    DURATION = 150;

  // canvas + geometry (must match the Remotion composition)
  var W = 1040,
    H = 560,
    A_SCALE = 1,
    MOTION_CX = 264;
  var CHEVRON =
    "M -130 150 L -28 -150 L 28 -150 L 130 150 L 72 150 L 0 -61.8 L -72 150 Z";
  var BAR = { x: -78, y: 5, w: 208, h: 50 };
  var FOOT = [130, 150];
  var BAR_BOTTOM = [250, 150];

  // timeline
  var F1S = 12,
    A_FALL = 22,
    F2S = 48,
    L_FALL = 14,
    L_IMPACT = F2S + L_FALL,
    SLIDE = 200,
    SLIDE_DUR = 24,
    F4S = 116,
    STAGE1 = 15;

  // --- little math kit (mirrors Remotion's interpolate / Easing / spring) ---
  function clamp01(v) {
    return v < 0 ? 0 : v > 1 ? 1 : v;
  }
  function easeInQuad(t) {
    return t * t;
  }
  function easeOutQuad(t) {
    return 1 - (1 - t) * (1 - t);
  }
  function interp(x, x0, x1, y0, y1, ease) {
    var t = clamp01((x - x0) / (x1 - x0));
    if (ease) t = ease(t);
    return y0 + t * (y1 - y0);
  }
  function lerp(s, a, b) {
    return a + s * (b - a); // s may exceed [0,1] (spring overshoot)
  }
  function ricochet(t, peak, dur, e, hops) {
    if (t <= 0) return 0;
    var start = 0,
      p = peak,
      d = dur;
    for (var i = 0; i < hops; i++) {
      if (t < start + d) {
        var u = (t - start) / d;
        return p * 4 * u * (1 - u); // parabolic hop, 0 -> peak -> 0
      }
      start += d;
      p *= e * e;
      d *= e;
    }
    return 0;
  }
  // analytic damped-spring step response (0 -> 1), like Remotion's spring()
  function spring(frame, fps, cfg) {
    var stiffness = cfg.stiffness,
      damping = cfg.damping,
      mass = cfg.mass;
    var t = frame / fps;
    if (t <= 0) return 0;
    var zeta = damping / (2 * Math.sqrt(stiffness * mass));
    var w0 = Math.sqrt(stiffness / mass);
    var x0 = -1; // start displacement from target
    if (zeta < 1) {
      var w1 = w0 * Math.sqrt(1 - zeta * zeta);
      var env = Math.exp(-zeta * w0 * t);
      return 1 + env * (x0 * Math.cos(w1 * t) + ((zeta * w0 * x0) / w1) * Math.sin(w1 * t));
    }
    var env2 = Math.exp(-w0 * t);
    return 1 + env2 * (x0 + w0 * x0 * t);
  }

  // --- per-frame values (a direct port of the composition) ------------------
  function values(frame) {
    var fall;
    if (frame <= F1S) fall = 0;
    else if (frame - F1S < A_FALL)
      fall = interp(frame - F1S, 0, A_FALL, 0, 90, easeInQuad);
    else fall = 90 - ricochet(frame - F1S - A_FALL, 7, 8, 0.45, 2);

    var barFall;
    if (frame < F2S) barFall = 0;
    else if (frame - F2S < L_FALL)
      barFall = interp(frame - F2S, 0, L_FALL, 0, 90, easeInQuad);
    else barFall = 90;

    var tSkid = frame - L_IMPACT;
    var hop = ricochet(tSkid, 26, 11, 0.6, 4);
    var slide = interp(tSkid, 0, SLIDE_DUR, 0, SLIDE, easeOutQuad);

    var chev = fall,
      barA1 = fall,
      barA2 = barFall,
      offX = slide;

    if (frame >= F4S) {
      hop = 0;
      if (frame < F4S + STAGE1) {
        var s1 = spring(frame - F4S, FPS, { damping: 16, mass: 0.9, stiffness: 120 });
        barA2 = lerp(s1, 90, 0);
        offX = Math.max(0, lerp(s1, SLIDE, 0));
        chev = 90;
        barA1 = 90;
      } else {
        barA2 = 0;
        offX = 0;
        var s2 = spring(frame - (F4S + STAGE1), FPS, { damping: 15, mass: 1, stiffness: 95 });
        chev = lerp(s2, 90, 0);
        barA1 = chev;
      }
    }

    return {
      chev: chev,
      barA1: barA1,
      barA2: barA2,
      offX: offX,
      hop: hop,
      opacity: interp(frame, 0, 4, 0, 1),
    };
  }

  // --- DOM ------------------------------------------------------------------
  var NS = "http://www.w3.org/2000/svg";

  function build(container, withShadow) {
    var markup =
      '<svg xmlns="' +
      NS +
      '" viewBox="0 0 ' +
      W +
      " " +
      H +
      '" preserveAspectRatio="xMidYMid meet" width="100%" height="100%" aria-hidden="true">' +
      (withShadow
        ? '<defs><filter id="ah-shadow" x="-40%" y="-40%" width="180%" height="180%">' +
          '<feDropShadow dx="0" dy="10" stdDeviation="10" flood-color="#000" flood-opacity="0.18"/>' +
          "</filter></defs>"
        : "") +
      '<g id="ah-group" fill="currentColor"' +
      (withShadow ? ' filter="url(#ah-shadow)"' : "") +
      ' transform="translate(' +
      W / 2 +
      " " +
      H / 2 +
      ") scale(" +
      A_SCALE +
      ") translate(" +
      -MOTION_CX +
      ' 0)">' +
      '<path id="ah-chev" d="' +
      CHEVRON +
      '"/>' +
      '<rect id="ah-bar" x="' +
      BAR.x +
      '" y="' +
      BAR.y +
      '" width="' +
      BAR.w +
      '" height="' +
      BAR.h +
      '"/>' +
      "</g></svg>";
    var doc = new DOMParser().parseFromString(markup, "image/svg+xml");
    var svg = doc.documentElement;
    container.appendChild(svg);
    return {
      group: svg.querySelector("#ah-group"),
      chev: svg.querySelector("#ah-chev"),
      bar: svg.querySelector("#ah-bar"),
    };
  }

  function render(els, frame, fade) {
    var v = values(frame);
    els.chev.setAttribute("transform", "rotate(" + v.chev + " " + FOOT[0] + " " + FOOT[1] + ")");
    els.bar.setAttribute(
      "transform",
      "translate(" +
        v.offX +
        " " +
        -v.hop +
        ") rotate(" +
        v.barA2 +
        " " +
        BAR_BOTTOM[0] +
        " " +
        BAR_BOTTOM[1] +
        ") rotate(" +
        v.barA1 +
        " " +
        FOOT[0] +
        " " +
        FOOT[1] +
        ")",
    );
    // fade in only on the first cycle, so a loop doesn't pulse on restart
    els.group.setAttribute("opacity", fade === false ? 1 : v.opacity);
  }

  function init(container, opts) {
    opts = opts || {};
    var loop = opts.loop != null ? opts.loop : container.dataset.loop !== "false";
    var replay = opts.replay != null ? opts.replay : container.dataset.replay;
    var withShadow = !(opts.shadow === false || container.dataset.shadow === "false");

    var els = build(container, withShadow);
    var reduce =
      typeof matchMedia === "function" &&
      matchMedia("(prefers-reduced-motion: reduce)").matches;

    if (reduce) {
      render(els, DURATION - 1); // resting A, no motion
      return { play: function () {}, els: els };
    }

    var raf = null,
      start = null,
      firstCycle = true;
    function tick(ts) {
      if (start == null) start = ts;
      var frame = ((ts - start) / 1000) * FPS;
      if (frame >= DURATION) {
        if (loop) {
          start = ts;
          frame = 0;
          firstCycle = false;
        } else {
          render(els, DURATION - 1, firstCycle);
          raf = null;
          return;
        }
      }
      render(els, frame, firstCycle);
      raf = requestAnimationFrame(tick);
    }
    function play() {
      if (raf) cancelAnimationFrame(raf);
      start = null;
      firstCycle = true;
      raf = requestAnimationFrame(tick);
    }

    render(els, 0);
    play();
    if (replay === "hover") container.addEventListener("mouseenter", play);
    if (replay === "click") container.addEventListener("click", play);

    return { play: play, els: els };
  }

  // auto-init + public API
  var ArnoldHero = { init: init };
  if (typeof window !== "undefined") {
    window.ArnoldHero = ArnoldHero;
    if (typeof document !== "undefined") {
      var boot = function () {
        var nodes = document.querySelectorAll("[data-arnold-hero]");
        for (var i = 0; i < nodes.length; i++) init(nodes[i]);
      };
      if (document.readyState === "loading")
        document.addEventListener("DOMContentLoaded", boot);
      else boot();
    }
  }
  if (typeof module !== "undefined" && module.exports) module.exports = ArnoldHero;
})();
