/*
 * Arnold animated loader — lightweight loading indicators using the A lettermark.
 *
 * Drop-in: give it a container and it builds + animates the A as a loader.
 *
 *   <div data-arnold-loader="pendulum"></div>
 *   <script src="arnold-loader.js"></script>
 *
 * The A is drawn with `currentColor`, so set the container's `color` to match
 * your brand. It is theme-aware for free and crisp at any size.
 *
 * Modes via data-arnold-loader attribute (or ArnoldLoader.init(el, opts)):
 *   pants     Crossbar slides down, bounces, springs back up. (1.67s loop) [default]
 *   pendulum  A rocks +/-15 deg around base pivot. Calm metronome. (1.5s loop)
 *   full      Same animation as the hero mark (tip->hold->recover). (5s loop)
 *   bounce    A upright; crossbar detaches, drops, bounces 3x. (1.5s loop)
 *   cursor    A tips to 90 deg once (becomes >_), crossbar pulses. Indeterminate.
 *
 * Options:
 *   data-size="48px"    CSS size (width & height)
 *   data-shadow="true"  enable a soft drop shadow
 *
 * Honours `prefers-reduced-motion` (shows the resting A, no motion).
 */
(function () {
  "use strict";

  var FPS = 30;

  // geometry (same SVG paths as arnold-hero.js)
  var CHEVRON =
    "M -130 150 L -22 -150 L 22 -150 L 130 150 L 88 150 L 0 -80 L -88 150 Z";
  var BAR = { x: -78, y: 10, w: 208, h: 36 };
  var FOOT = [130, 150];
  var BAR_BOTTOM = [250, 150];
  var VIEWBOX = "-140 -162 280 324";

  // mode durations (in frames)
  var PENDULUM_DUR = 45;
  var FULL_DUR = 150;
  var BOUNCE_DUR = 45;
  var PANTS_DUR = 50;

  // full mode timeline (same as arnold-hero.js)
  var FULL_F1S = 12,
    FULL_A_FALL = 22,
    FULL_F2S = 48,
    FULL_L_FALL = 14,
    FULL_L_IMPACT = 62,
    FULL_SLIDE = 200,
    FULL_SLIDE_DUR = 24,
    FULL_F4S = 116,
    FULL_STAGE1 = 15;

  // cursor mode constants
  var CURSOR_TIP_DUR = 20;
  var CURSOR_RECOVER_DUR = 30;

  // --- little math kit (mirrors arnold-hero.js) --------------------------------
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
    return a + s * (b - a);
  }
  function ricochet(t, peak, dur, e, hops) {
    if (t <= 0) return 0;
    var start = 0,
      p = peak,
      d = dur;
    for (var i = 0; i < hops; i++) {
      if (t < start + d) {
        var u = (t - start) / d;
        return p * 4 * u * (1 - u);
      }
      start += d;
      p *= e * e;
      d *= e;
    }
    return 0;
  }
  function spring(frame, fps, cfg) {
    var stiffness = cfg.stiffness,
      damping = cfg.damping,
      mass = cfg.mass;
    var t = frame / fps;
    if (t <= 0) return 0;
    var zeta = damping / (2 * Math.sqrt(stiffness * mass));
    var w0 = Math.sqrt(stiffness / mass);
    var x0 = -1;
    if (zeta < 1) {
      var w1 = w0 * Math.sqrt(1 - zeta * zeta);
      var env = Math.exp(-zeta * w0 * t);
      return 1 + env * (x0 * Math.cos(w1 * t) + ((zeta * w0 * x0) / w1) * Math.sin(w1 * t));
    }
    var env2 = Math.exp(-w0 * t);
    return 1 + env2 * (x0 + w0 * x0 * t);
  }

  // --- per-mode value functions ------------------------------------------------

  function pendulumValues(frame) {
    var angle = 15 * Math.sin((frame / PENDULUM_DUR) * 2 * Math.PI);
    return { chev: angle, barA1: angle, barA2: 0, offX: 0, hop: 0, barOpacity: 1 };
  }

  function fullValues(frame) {
    var fall;
    if (frame <= FULL_F1S) fall = 0;
    else if (frame - FULL_F1S < FULL_A_FALL)
      fall = interp(frame - FULL_F1S, 0, FULL_A_FALL, 0, 90, easeInQuad);
    else fall = 90 - ricochet(frame - FULL_F1S - FULL_A_FALL, 7, 8, 0.45, 2);

    var barFall;
    if (frame < FULL_F2S) barFall = 0;
    else if (frame - FULL_F2S < FULL_L_FALL)
      barFall = interp(frame - FULL_F2S, 0, FULL_L_FALL, 0, 90, easeInQuad);
    else barFall = 90;

    var tSkid = frame - FULL_L_IMPACT;
    var hop = ricochet(tSkid, 26, 11, 0.6, 4);
    var slide = interp(tSkid, 0, FULL_SLIDE_DUR, 0, FULL_SLIDE, easeOutQuad);

    var chev = fall,
      barA1 = fall,
      barA2 = barFall,
      offX = slide;

    if (frame >= FULL_F4S) {
      hop = 0;
      if (frame < FULL_F4S + FULL_STAGE1) {
        var s1 = spring(frame - FULL_F4S, FPS, { damping: 16, mass: 0.9, stiffness: 120 });
        barA2 = lerp(s1, 90, 0);
        offX = Math.max(0, lerp(s1, FULL_SLIDE, 0));
        chev = 90;
        barA1 = 90;
      } else {
        barA2 = 0;
        offX = 0;
        var s2 = spring(frame - (FULL_F4S + FULL_STAGE1), FPS, { damping: 15, mass: 1, stiffness: 95 });
        chev = lerp(s2, 90, 0);
        barA1 = chev;
      }
    }

    return { chev: chev, barA1: barA1, barA2: barA2, offX: offX, hop: hop, barOpacity: 1 };
  }

  function bounceValues(frame) {
    var detachFrame = 5;
    var fallDur = 8;

    var barA2;
    if (frame < detachFrame) {
      barA2 = 0;
    } else if (frame - detachFrame < fallDur) {
      barA2 = interp(frame - detachFrame, 0, fallDur, 0, 90, easeInQuad);
    } else {
      barA2 = 90;
    }

    var tBounce = frame - (detachFrame + fallDur);
    var hop = ricochet(tBounce, 30, 10, 0.55, 3);

    return { chev: 0, barA1: 0, barA2: barA2, offX: 0, hop: hop, barOpacity: 1 };
  }

  // --- mode: pants (1.67s = 50 frames @ 30fps) ---
  // crossbar slides down (like pants falling), bounces at the bottom, then springs back up
  function pantsValues(frame) {
    // Phase 1: crossbar slides down (frames 0–12)
    // The bar translates downward along the Y axis (from y=5 to y=100)
    var slideDown = 95; // total distance to fall
    var fallDur = 12;
    var bounceDur = 18;
    var holdDur = 5;
    var recoverStart = fallDur + bounceDur + holdDur; // frame 35

    var barY = 0;

    if (frame < fallDur) {
      // falling down with gravity (ease-in)
      barY = interp(frame, 0, fallDur, 0, slideDown, easeInQuad);
    } else if (frame < fallDur + bounceDur) {
      // at bottom, bouncing
      var tBounce = frame - fallDur;
      barY = slideDown - ricochet(tBounce, 20, 9, 0.5, 3);
    } else if (frame < recoverStart) {
      // hold at bottom briefly
      barY = slideDown;
    } else {
      // spring pull-back up
      var s = spring(frame - recoverStart, FPS, { damping: 14, mass: 0.8, stiffness: 140 });
      barY = lerp(s, slideDown, 0);
    }

    // barY is a vertical translation offset — we pass it as negative hop (hop moves up, we want down)
    return { chev: 0, barA1: 0, barA2: 0, offX: 0, hop: -barY, barOpacity: 1 };
  }

  // cursor mode is stateful, handled inline in the loop

  // --- DOM --------------------------------------------------------------------
  var NS = "http://www.w3.org/2000/svg";
  var idCounter = 0;

  function build(container, opts) {
    var size = opts.size || "48px";
    var withShadow = opts.shadow || false;
    var uid = "al-" + (++idCounter);

    var markup =
      '<svg xmlns="' + NS + '" viewBox="' + VIEWBOX +
      '" width="' + size + '" height="' + size +
      '" preserveAspectRatio="xMidYMid meet" aria-hidden="true" role="img"' +
      ' style="display:block;overflow:visible">' +
      (withShadow
        ? '<defs><filter id="' + uid + '-shadow" x="-40%" y="-40%" width="180%" height="180%">' +
          '<feDropShadow dx="0" dy="4" stdDeviation="4" flood-color="#000" flood-opacity="0.15"/>' +
          "</filter></defs>"
        : "") +
      '<g id="' + uid + '-group" fill="currentColor"' +
      (withShadow ? ' filter="url(#' + uid + '-shadow)"' : "") + ">" +
      '<path id="' + uid + '-chev" d="' + CHEVRON + '"/>' +
      '<rect id="' + uid + '-bar" x="' + BAR.x + '" y="' + BAR.y +
      '" width="' + BAR.w + '" height="' + BAR.h + '"/>' +
      "</g></svg>";

    var doc = new DOMParser().parseFromString(markup, "image/svg+xml");
    var svg = doc.documentElement;
    container.appendChild(container.ownerDocument.importNode(svg, true));
    var imported = container.lastElementChild;

    return {
      svg: imported,
      group: imported.querySelector("#" + uid + "-group"),
      chev: imported.querySelector("#" + uid + "-chev"),
      bar: imported.querySelector("#" + uid + "-bar"),
    };
  }

  function render(els, v) {
    els.chev.setAttribute(
      "transform",
      "rotate(" + v.chev + " " + FOOT[0] + " " + FOOT[1] + ")"
    );
    els.bar.setAttribute(
      "transform",
      "translate(" + v.offX + " " + -v.hop + ") rotate(" +
        v.barA2 + " " + BAR_BOTTOM[0] + " " + BAR_BOTTOM[1] +
        ") rotate(" + v.barA1 + " " + FOOT[0] + " " + FOOT[1] + ")"
    );
    if (v.barOpacity !== undefined && v.barOpacity !== 1) {
      els.bar.setAttribute("opacity", v.barOpacity);
    } else {
      els.bar.removeAttribute("opacity");
    }
  }

  // --- init -------------------------------------------------------------------
  function init(container, opts) {
    opts = opts || {};
    var mode = opts.mode || container.getAttribute("data-arnold-loader") || "pants";
    var size = opts.size || container.dataset.size || "48px";
    var withShadow = opts.shadow != null
      ? opts.shadow
      : container.dataset.shadow === "true";

    var els = build(container, { size: size, shadow: withShadow });

    // reduced motion: show resting A
    var reduce =
      typeof matchMedia === "function" &&
      matchMedia("(prefers-reduced-motion: reduce)").matches;

    if (reduce) {
      render(els, { chev: 0, barA1: 0, barA2: 0, offX: 0, hop: 0, barOpacity: 1 });
      return { play: function () {}, stop: function () {}, destroy: function () { destroy(els, container); } };
    }

    var raf = null;
    var start = null;
    var running = false;
    var stopping = false;
    var stopStart = null;
    var cursorTipped = false;
    var cursorTipStart = null;

    function getDuration() {
      if (mode === "pendulum") return PENDULUM_DUR;
      if (mode === "full") return FULL_DUR;
      if (mode === "bounce") return BOUNCE_DUR;
      if (mode === "pants") return PANTS_DUR;
      return 0; // cursor is indeterminate
    }

    function tick(ts) {
      if (start == null) start = ts;
      var elapsed = (ts - start) / 1000;
      var frame = elapsed * FPS;
      var dur = getDuration();
      var v;

      if (mode === "cursor") {
        v = cursorTick(frame, ts);
      } else {
        // loop mode
        if (dur > 0) frame = frame % dur;
        if (mode === "pendulum") v = pendulumValues(frame);
        else if (mode === "full") v = fullValues(frame);
        else if (mode === "bounce") v = bounceValues(frame);
        else if (mode === "pants") v = pantsValues(frame);
      }

      if (v) render(els, v);

      // check if stop was requested for non-cursor modes
      if (stopping && mode !== "cursor") {
        // finish the current cycle
        var totalElapsed = (ts - start) / 1000 * FPS;
        var posInCycle = totalElapsed % dur;
        if (posInCycle < 2 || totalElapsed >= stopStart + dur) {
          // reached end of cycle
          render(els, { chev: 0, barA1: 0, barA2: 0, offX: 0, hop: 0, barOpacity: 1 });
          running = false;
          stopping = false;
          raf = null;
          return;
        }
      }

      if (running) {
        raf = requestAnimationFrame(tick);
      }
    }

    function cursorTick(frame, ts) {
      if (stopping) {
        // spring recovery
        if (stopStart == null) stopStart = ts;
        var recoverFrame = ((ts - stopStart) / 1000) * FPS;
        var s = spring(recoverFrame, FPS, { damping: 15, mass: 1, stiffness: 95 });
        var angle = lerp(s, 90, 0);
        if (recoverFrame >= CURSOR_RECOVER_DUR) {
          running = false;
          stopping = false;
          raf = null;
          return { chev: 0, barA1: 0, barA2: 0, offX: 0, hop: 0, barOpacity: 1 };
        }
        return { chev: angle, barA1: angle, barA2: 0, offX: 0, hop: 0, barOpacity: 1 };
      }

      if (!cursorTipped) {
        // tip phase
        if (cursorTipStart == null) cursorTipStart = frame;
        var tipFrame = frame - cursorTipStart;
        if (tipFrame >= CURSOR_TIP_DUR) {
          cursorTipped = true;
          return { chev: 90, barA1: 90, barA2: 0, offX: 0, hop: 0, barOpacity: cursorPulse(ts) };
        }
        var angle2 = interp(tipFrame, 0, CURSOR_TIP_DUR, 0, 90, easeInQuad);
        return { chev: angle2, barA1: angle2, barA2: 0, offX: 0, hop: 0, barOpacity: 1 };
      }

      // pulsing cursor
      return { chev: 90, barA1: 90, barA2: 0, offX: 0, hop: 0, barOpacity: cursorPulse(ts) };
    }

    function cursorPulse(ts) {
      var t = ts / 1000;
      return 0.3 + 0.7 * Math.abs(Math.sin(t * Math.PI * 1.5));
    }

    function play() {
      if (running) return;
      running = true;
      stopping = false;
      stopStart = null;
      start = null;
      cursorTipped = false;
      cursorTipStart = null;
      raf = requestAnimationFrame(tick);
    }

    function stop() {
      if (!running) return;
      if (mode === "cursor") {
        stopping = true;
        stopStart = null;
      } else {
        stopping = true;
        var elapsed = (performance.now() - (start || performance.now())) / 1000 * FPS;
        stopStart = elapsed;
      }
    }

    function destroyInstance() {
      running = false;
      stopping = false;
      if (raf) {
        cancelAnimationFrame(raf);
        raf = null;
      }
      destroy(els, container);
    }

    // render resting state then start
    render(els, { chev: 0, barA1: 0, barA2: 0, offX: 0, hop: 0, barOpacity: 1 });
    play();

    return { play: play, stop: stop, destroy: destroyInstance };
  }

  function destroy(els, container) {
    if (els.svg && els.svg.parentNode) {
      els.svg.parentNode.removeChild(els.svg);
    }
  }

  // --- auto-init + public API -------------------------------------------------
  var ArnoldLoader = { init: init };

  if (typeof window !== "undefined") {
    window.ArnoldLoader = ArnoldLoader;
    if (typeof document !== "undefined") {
      var boot = function () {
        var nodes = document.querySelectorAll("[data-arnold-loader]");
        for (var i = 0; i < nodes.length; i++) init(nodes[i]);
      };
      if (document.readyState === "loading")
        document.addEventListener("DOMContentLoaded", boot);
      else boot();
    }
  }
  if (typeof module !== "undefined" && module.exports) module.exports = ArnoldLoader;
})();
