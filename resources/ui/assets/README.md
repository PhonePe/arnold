# Arnold logo assets

All assets use your theme colours:

| token        | light     | dark      |
| ------------ | --------- | --------- |
| `--accent`   | `#E24329` | `#F2543A` |
| `--surface-2`| `#ffffff` | `#333338` |

---

## 1. Header mark — `arnold-mark.svg`

The A glyph on its own, painted with `currentColor`. Put it **inside** your existing
`.seal` (which already supplies the accent background, 8px grid and inner highlight).

**It must be inlined** for `currentColor` / theme to work — don't load it via
`<img src>` (that renders it black). Paste the markup, or import it as an inline
SVG/React component.

```html
<div class="seal">
  <svg viewBox="-140 -162 280 324" fill="currentColor" aria-hidden="true">
    <path d="M-130 150 L-28 -150 L28 -150 L130 150 L72 150 L0 -61.8 L-72 150 Z"/>
    <rect x="-78" y="5" width="208" height="50"/>
  </svg>
</div>
```

```css
/* size it inside the 38px seal, above the grid */
.seal svg { position: relative; z-index: 1; width: 62%; height: 62%; display: block; }
```

It inherits `color: var(--surface-2)` from `.seal`, so it flips with the theme
automatically. See `seal-demo.html` for a live light/dark preview.

---

## 2. Favicon — `favicon.svg`

A self-contained seal (accent square + grid + inner highlight + A) that adapts to
light/dark on its own via `prefers-color-scheme` (works in Safari/Firefox; falls
back to light elsewhere).

```html
<link rel="icon" type="image/svg+xml" href="/favicon.svg" />
```

If you also need a raster fallback (`favicon.ico` / apple-touch-icon), open this
SVG at 180×180 / 512×512 and export PNGs.

---

## 3. Animated reveal — vector (recommended) — `arnold-hero.js`

The full tip-over → `>_` → stand-back-up animation as **vector + JS**: ~a few KB,
crisp at any size, theme-aware (uses `currentColor`), and far cheaper to run than a
video. This is the recommended way to ship the animation.

Three pieces (see `arnold-hero.html` for a working demo):

```html
<!-- 1. a styled container: accent background + grid + the surface-2 colour -->
<style>
  .arnold-hero {
    position: relative; width: 520px; height: 280px;  /* any size / aspect */
    background: var(--accent); color: var(--surface-2); overflow: hidden;
  }
  .arnold-hero::before {            /* engraved grid */
    content: ""; position: absolute; inset: 0;
    background-image:
      linear-gradient(to right, rgba(255,255,255,.08) 1px, transparent 1px),
      linear-gradient(to bottom, rgba(255,255,255,.08) 1px, transparent 1px);
    background-size: 20px 20px;
  }
  .arnold-hero svg { position: relative; z-index: 1; }
</style>

<!-- 2. the container -->
<div class="arnold-hero" data-arnold-hero data-replay="hover"></div>

<!-- 3. the script (builds + animates the A inside) -->
<script src="arnold-hero.js"></script>
```

Options (data-attributes, or `ArnoldHero.init(el, opts)`):
`data-loop="false"` (play once), `data-replay="hover|click"`, `data-shadow="false"`.
It honours `prefers-reduced-motion` (shows the resting A). **Loops by default**, and
only fades in on the first cycle so the loop doesn't pulse.

## 4. Animated reveal — video fallback — `arnold-hero-light.mp4` / `…-dark.mp4`

Same animation baked to MP4 (5s, 1040×560). Only reach for this if you specifically
want a `<video>` embed. Colours are baked in, so load the file matching the theme:

```html
<video autoplay muted playsinline loop src="/arnold-hero-light.mp4"></video>
```

(The MP4s have no intro fade, so they loop seamlessly.)

> Vector (#3) is lighter, crisper and theme-aware — prefer it. The MP4s are kept
> only as a drop-in `<video>` fallback.

---

Regenerate the videos from the Remotion project with:

```bash
npx remotion render MyComp arnold-hero-light.mp4 --props='{"theme":"light"}'
npx remotion render MyComp arnold-hero-dark.mp4  --props='{"theme":"dark"}'
```
