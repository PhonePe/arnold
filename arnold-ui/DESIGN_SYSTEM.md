# Arnold · Lattice — Design System

> The canonical reference for building consistent, high-quality UI across the Arnold code-review platform.
> All LLMs and engineers generating UI code for this project **must** follow this document.
>
> The system is internally named **Lattice** (kami / 紙 — "paper"). It is a warm, editorial, document-grade aesthetic: a
> paper canvas under an engraved grid, a single vermilion signal accent, sharp corners, and structural hairlines. A
> pixel-accurate reference implementation of every screen lives in the design source the team iterates on; this document
> is the implementation contract for the React app.

---

## 1. Brand Identity

| Property             | Value                                                                                             |
|----------------------|---------------------------------------------------------------------------------------------------|
| Product name         | **Arnold**                                                                                        |
| Wordmark subtitle    | CODE REVIEW                                                                                       |
| Internal system name | **Lattice** (kami / 紙)                                                                            |
| Personality          | Editorial, precise, document-grade, calm. Engineering-tool seriousness without coldness.          |
| Visual tone          | Warm paper canvas, engraved lattice grid, one decisive accent, sharp corners, hairline structure. |

### 1.1 The Seal

The brand mark is the **seal**: the geometric "A" lettermark on a vermilion square with an engraved 8px grid and an
inner highlight. Use the `<Seal>` component (`src/components/ui/Seal.jsx`). The glyph is painted with `currentColor` and
inherits `--on-accent`, so it flips with the theme.

- The favicon (`public/favicon.svg`) is a self-contained seal that adapts to light/dark via `prefers-color-scheme`.
- The bare glyph lives at `public/arnold-mark.svg` and must be **inlined** (not loaded via `<img>`) for `currentColor`
  to work.
- An animated version of the mark drives the splash screen and the "Run Analysis" loader — see §13.

---

## 2. Color Palette

Lattice is **light-first** with a first-class dark theme. All colors are CSS custom properties in `globals.css`, toggled
by `data-theme` on `<html>`, and mapped to Tailwind v4 utilities via `@theme inline`.

> **Theme switching:** the active theme is `data-theme="light"` (default) or `data-theme="dark"`. A bootstrap script in
`layout.js` sets it before paint (system preference, then `localStorage['arnold.theme']`). The `<ThemeToggle>` in the
> top bar flips and persists it.

### 2.1 Surfaces (Paper → Raised → Highest)

Three surface levels, separated by **hairline borders**, not just tonal shifts (see §3).

| Token (Tailwind) | CSS var       | Light     | Dark      | Usage                                  |
|------------------|---------------|-----------|-----------|----------------------------------------|
| `bg`             | `--bg`        | `#f4f2ef` | `#1b1b1e` | Page canvas (carries the lattice grid) |
| `surface`        | `--surface`   | `#faf9f7` | `#29292C` | Sidebar, cards, panels, rows           |
| `surface-2`      | `--surface-2` | `#ffffff` | `#333338` | Inputs, code, popovers, diffs          |

> Legacy Material-scale aliases (`surface-container`, `surface-container-low`, `surface-container-high`,
`surface-container-highest`, `surface-container-lowest`) are mapped onto these three levels for backwards compatibility.
**New code should prefer `bg` / `surface` / `surface-2`.**

### 2.2 Ink (Text)

| Token   | CSS var   | Light     | Dark      | Usage                           |
|---------|-----------|-----------|-----------|---------------------------------|
| `fg`    | `--fg`    | `#29292C` | `#f2f1ee` | Primary text                    |
| `muted` | `--muted` | `#6a6a72` | `#a6a5ac` | Secondary text, meta, labels    |
| `faint` | `--faint` | `#9a9aa3` | `#6f6f78` | Placeholders, gutters, disabled |

> Legacy aliases: `on-surface` → `fg`, `on-surface-muted` → `muted`, `on-surface-faint` → `faint`,
`on-surface-variant` → `fg`.

### 2.3 Structure (Hairlines)

| Token      | CSS var      | Light     | Dark      | Usage                                  |
|------------|--------------|-----------|-----------|----------------------------------------|
| `border`   | `--border`   | `#e3e0da` | `#3a3a40` | Default structural hairline            |
| `border-2` | `--border-2` | `#d2cec6` | `#4b4b53` | Stronger hairline — controls, dividers |

### 2.4 Accent (Vermilion Signal)

The single decisive accent. One signal color so it always means "attention here".

| Token        | CSS var         | Light     | Dark      | Usage                                      |
|--------------|-----------------|-----------|-----------|--------------------------------------------|
| `accent`     | `--accent`      | `#E24329` | `#F2543A` | Primary CTAs, active nav, links, the seal  |
| `accent-ink` | `--accent-ink`  | `#c2341c` | `#ff7458` | Hover/pressed accent; accent text on paper |
| `on-accent`  | `--on-accent`   | `#ffffff` | `#ffffff` | Text/glyphs on accent                      |
| —            | `--accent-soft` | mix 12%   | mix 12%   | Soft accent fill (active nav bg, tints)    |

> Legacy aliases: `primary` → `accent`, `primary-container` → `accent-ink`, `on-primary` → `on-accent`.

### 2.5 Tertiary (Amber)

| Token          | CSS var          | Light     | Dark      | Usage                                    |
|----------------|------------------|-----------|-----------|------------------------------------------|
| `tertiary`     | `--tertiary`     | `#DDA15E` | `#E6B274` | Caution, "analysing", confidence figures |
| `tertiary-ink` | `--tertiary-ink` | `#b97f3c` | `#f0c089` | Tertiary text/hover                      |

### 2.6 Status

| Token  | CSS var  | Light     | Usage                       |
|--------|----------|-----------|-----------------------------|
| `ok`   | `--ok`   | `#2e9e6b` | Passing, merged, success    |
| `warn` | `--warn` | `#DDA15E` | Caution (= tertiary)        |
| `bad`  | `--bad`  | `#E24329` | Errors, critical (= accent) |

> Legacy aliases: `status-success` → `ok`, `status-warning` → `warn`, `status-error` → `bad`, `status-info` →
`sev-maint` (blue), `status-neutral` → `muted`.

### 2.7 Severity Taxonomy

A calibrated scale for finding severities. The vermilion signal stays dominant; everything else is warm and muted by
comparison.

| Token          | Hex       | Meaning                                   |
|----------------|-----------|-------------------------------------------|
| `sev-security` | `#d23f57` | Security                                  |
| `sev-critical` | `#E24329` | Critical (= accent)                       |
| `sev-bug`      | `#e08a2b` | Bug                                       |
| `sev-issue`    | `#DDA15E` | Issue                                     |
| `sev-perf`     | `#8a9b2e` | Performance                               |
| `sev-practice` | `#2e9e6b` | Best practice                             |
| `sev-maint`    | `#2b8cc4` | Maintainability / info / links-in-context |
| `sev-enh`      | `#5566d6` | Enhancement                               |
| `sev-typo`     | `#8a93a3` | Typo                                      |
| `sev-nit`      | `#aab2c0` | Nit                                       |

---

## 3. The Hairline Rule (replaces the old "No-Line" Rule)

> **Structure in Lattice is drawn with 1px hairline borders.** This is the inverse of the previous dark-first system.

- Cards, rows, inputs, diffs, panels, the sidebar, and the top bar all carry a `border border-border` (or `border-2` for
  stronger separation).
- Sidebar/top bar use a single edge hairline (`border-r` / `border-b`).
- The **engraved lattice grid** (a 30px CSS grid at ~4% ink) sits on the page canvas behind everything (`body`
  background, or the `.lattice-grid` utility). Cards sit on `surface` and read as paper laid over the grid.
- Accent left-bars (e.g. active nav `w-[3px]`, `AnalysisCard` `border-l-[3px]`) are intentional markers, not structure.

There is **no card-shadow elevation**. Depth comes from the surface step (`bg` → `surface` → `surface-2`) plus the
hairline. Soft shadows are reserved for genuinely floating layers (menus, popovers, drawers, toasts) — see §5.

---

## 4. Typography

### 4.1 Font Stack

| Role                 | Font                | CSS var          | Tailwind class   |
|----------------------|---------------------|------------------|------------------|
| Display / headings   | **Space Grotesk**   | `--font-display` | `font-display`   |
| Body / UI            | **system UI stack** | `--font-sans`    | (default `body`) |
| Mono / labels / code | **IBM Plex Mono**   | `--font-mono`    | `font-mono`      |

Fonts are loaded from bundled files via `next/font/local` in `layout.js` (Space Grotesk + IBM Plex Mono). Body text uses the native system
stack for speed and a crisp document feel.

> Legacy aliases: `font-headline` → `font-display`, `font-body` → system sans, `font-code` → `font-mono`.

### 4.2 Type Patterns

| Pattern             | Recipe                                                                                 |
|---------------------|----------------------------------------------------------------------------------------|
| Page title          | `font-display text-[clamp(30px,4vw,42px)] font-bold tracking-[-0.02em] leading-[1.05]` |
| Section title       | `font-display text-[21px] font-bold tracking-[-0.01em]`                                |
| Card title          | `font-display text-lg font-bold tracking-tight`                                        |
| Big stat figure     | `font-display text-[40px] font-bold tracking-[-0.03em] leading-none`                   |
| Eyebrow / overline  | `font-mono text-[11px] uppercase tracking-[0.12em] text-accent` (use `<Overline>`)     |
| Field label         | `font-mono text-[11px] uppercase tracking-[0.06em] text-muted` (use `<Label>`)         |
| Body                | `text-[15px] text-fg` / meta `text-[13px] text-muted`                                  |
| Meta / IDs / status | `font-mono` (IBM Plex Mono), tabular feel                                              |
| Code / diffs        | `font-mono text-[12.5px] leading-[1.65]`                                               |

In the live review workspace, use 14px for chapter names, file paths, steps, source code, and explanatory text, with 12px secondary labels. Toolbar buttons retain the shared Button size scale; inputs use the shared 13px body typography. The review header follows the standard page-title and 13px metadata patterns. Navigation chapter and step titles use the body font with semibold weight. In the main AI overview, chapter headings use 18px display text in their original case; reserve the display font for page and section headings.

### 4.3 Rules

- **Eyebrows are vermilion and mono** — they are the signature label of the system. Quiet variants:
  `<Overline tone="muted">` / `tone="faint"`.
- **Numbers, IDs, statuses, metadata, timestamps** are always **mono** (IBM Plex Mono).
- **Headlines and stat figures** are always **Space Grotesk**, tightly tracked.
- **Links** use `text-accent` / `text-accent-ink` — **never** standard blue.

---

## 5. Elevation & Depth

Depth is functional and minimal.

1. **Surface step + hairline** is the primary method: `bg` → `surface` → `surface-2`, each separated by a `border`.
2. **No shadows on cards or rows.**
3. **Floating layers only** (menus, popovers, drawers, toasts) may use a soft ambient shadow, e.g.
   `shadow-[0_14px_38px_rgba(28,26,22,0.16)]`.
4. **Hover** on interactive rows/cards may add a subtle lift shadow (`shadow-[0_4px_16px_rgba(28,26,22,0.05)]`) and/or
   shift `border` → `border-2` / `accent`.

The `.accent-panel` utility paints a vermilion surface with its own faint engraved grid — used for the "Run Analysis"
CTA and hero/marketing surfaces.

---

## 6. Border Radius

> **Sharp corners everywhere.** `--radius`, `--radius-sm`, `--radius-lg` are all `0`.

Use `rounded-none` explicitly on new elements. The only rounded shapes are:

| Shape                               | Radius         | Why                        |
|-------------------------------------|----------------|----------------------------|
| Avatars, status dots, severity dots | `rounded-full` | Circular by nature         |
| Meters / progress bars              | `rounded-full` | Reads correctly as a meter |
| Nav-item count badges               | `rounded-full` | Pill counters              |

Everything else — buttons, cards, inputs, badges, tags, diffs, panels — is **square**.

---

## 7. Spacing & Layout

```
┌──────────────────────────────────────────────────────────────┐
│ Top bar (h-60px, sticky, border-b, frosted bg)               │
├────────────────┬─────────────────────────────────────────────┤
│ Sidebar        │ Main content (px-10 py-8, transparent so the│
│ (w-248px)      │ lattice grid shows through)                 │
│ surface,       │                                             │
│ border-r       │  ┌─ surface + border ──────────────────┐    │
│                │  │ Card / panel / row                   │    │
│ ── Governance  │  └──────────────────────────────────────┘    │
│ Run Analysis   ├─────────────────────────────────────────────┤
│ User chip      │ Status bar (h-40px, border-t, surface)      │
└────────────────┴─────────────────────────────────────────────┘
```

- Content max width: `1680px`, centered.
- Use the Tailwind spacing scale. Common: card padding `p-5`, row padding `px-4 py-3`, section gaps `gap-4`/`gap-7`,
  page-head margin `mb-6`.
- The main scroll area is **transparent** so the body's lattice grid is visible behind cards.

---

## 8. Iconography

- Library: **Lucide React** (`lucide-react`).
- Default stroke width: **1.8** for nav/UI icons (lighter than Lucide's default 2).
- Sizes: inline `w-4 h-4` (16px), nav/top-bar `w-[17px]`–`w-[18px]`, card headers `w-5 h-5`, empty states `w-12 h-12` at
  `strokeWidth={1.5}`.
- Persona/marketplace avatars in the reference use kanji glyphs on colored squares; if rebuilding that view, follow the
  reference.

---

## 9. Components

### 9.1 Structure

```
src/components/
├── ui/           # Primitives — Seal, Button, Badge, Input, Label, Card,
│                 #   Avatar, StatusIndicator, SearchInput, Tabs, ProgressBar,
│                 #   CodeBlock, StatCard, Overline
├── layout/       # Shells — Sidebar, TopNav, AppShell, AppLayout, Container,
│                 #   StatusBar, ThemeToggle, Splash, RunButton
└── composite/    # Domain — MRListItem, AnalysisCard, DiffViewer, RuleCard,
                  #   RuleLevelCard, KnowledgeCard, FindingBadgeGroup
```

### 9.2 Component API (unchanged)

```jsx
export function ComponentName({
                                  variant = 'default',
                                  size = 'md',
                                  className = '',
                                  children,
                                  ...props
                              }) { /* ... */
}
```

Every component accepts `className`, spreads `...props` onto its root, uses **named exports**, and starts with
`'use client'`.

### 9.3 Button variants

| Variant                 | Styling                                                          |
|-------------------------|------------------------------------------------------------------|
| `primary`               | Flat `accent` bg, `on-accent` text, `hover:accent-ink`           |
| `secondary` / `outline` | Hairline `border-2`, `surface`/transparent bg, `hover:border-fg` |
| `inverted`              | `fg` bg, `surface` text                                          |
| `ghost`                 | Transparent, `muted` text, ink-wash hover, accent text on hover  |
| `ok`                    | `ok` bg, white text                                              |
| `danger`                | Hairline border, `bad` text, `hover:border-bad`                  |
| `link`                  | `accent-ink` text, underline on hover                            |

Sizes: `xs`, `sm`, `md` (default, `h-9`), `lg`. CTA buttons are **flat vermilion** — there is no gradient in Lattice.

### 9.4 Inputs

Hairline `border-2` on `surface-2`, sharp corners. Focus = `border-accent` + a soft `ring-[3px]` in `--accent-soft`. No
bottom-only underline.

### 9.5 Badges / Tags

Mono, uppercase, sharp, with soft tinted backgrounds drawn from the severity taxonomy (
`<Badge variant="security|performance|critical|info|…">`). The vermilion signal stays dominant.

### 9.6 Diffs

Hairline border, mono header, `surface-2` body. Line tint by `type`: `add` (green wash), `del` (red wash), `flag` (
accent-soft). Gutters mono and muted.

### 9.7 Status & severity atoms

- `<StatusIndicator status="success|warning|error|info|accent|neutral">` — mono, uppercase, dot + label. `warning`
  pulses.
- Status semantics: **Analysing** → warning/amber (pulsing dot); **Review ready** → accent; **Merged/complete** → ok; *
  *Issue/critical** → bad.

---

## 10. Interactive States

| State              | Treatment                                                                                                                                                        |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Focus              | `focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-bg`, or the `.focus-ring` helper (sharp accent-soft outline) |
| Hover (buttons)    | Darken to `accent-ink` / shift `border` → `border-fg`                                                                                                            |
| Hover (rows/cards) | `border` → `border-2` or `accent`; optional soft lift shadow                                                                                                     |
| Active nav         | `bg-[var(--accent-soft)]` + `text-accent-ink` + a `w-[3px]` accent left-bar                                                                                      |
| Active tab         | Sharp `accent` underline rail (or raised chip in `Tabs variant="segment"`)                                                                                       |
| Disabled           | `disabled:opacity-50 disabled:pointer-events-none`                                                                                                               |
| Analysing          | Pulsing amber dot (`animate-pulse`)                                                                                                                              |

---

## 11. Theming (light ⇄ dark)

- Default theme is **light** (warm paper). Dark is a charcoal counterpart.
- `data-theme` on `<html>` drives everything; the bootstrap script in `layout.js` sets it pre-paint to avoid flashes.
- `<ThemeToggle>` (top bar) flips and persists to `localStorage['arnold.theme']`; it mirrors the live value via
  `useSyncExternalStore` on the `data-theme` attribute.
- All tokens are theme-aware. **Never hardcode hex values** in components — always use the Tailwind token utilities (
  `bg-surface`, `text-muted`, `border-border`, `text-accent`, …) or `var(--token)` for color-mix needs.

---

## 12. Do's and Don'ts

### Do

- Use **hairline borders** (`border-border`) for structure, on every card/row/panel.
- Keep the page canvas transparent so the **lattice grid** shows through.
- Use **sharp corners** (`rounded-none`) for everything except avatars/dots/meters/count-pills.
- Use **flat vermilion** for primary CTAs.
- Use **Space Grotesk** for headings/figures, **IBM Plex Mono** for labels/IDs/status/code, system sans for body.
- Use **mono vermilion eyebrows** (`<Overline>`) to head sections.
- Use the **single accent** for "attention" only; severities use the taxonomy.
- Use the token utilities so both themes work automatically.

### Don't

- Don't use the old terracotta/`#ffb4a6`, the CTA gradient, Manrope/Inter/JetBrains Mono, or the dark-only palette —
  they are removed.
- Don't use rounded corners on controls/cards.
- Don't add drop shadows to cards or rows (floating layers only).
- Don't use standard blue for links (use `text-accent-ink`; blue is reserved for `sev-maint`).
- Don't hardcode hex colors in components.
- Don't use TypeScript (JavaScript/JSX only).
- Don't import component libraries beyond React, Tailwind, and lucide-react.

---

## 13. Brand Animations

The animated "A" lettermark is implemented in `src/lib/arnold-mark.js` (ported from the design source, no external
deps):

- `initHero(el, { loop, shadow })` — the full "tip-over → `>_` → stand-back-up" reveal. Drives the **splash**
  (`<Splash>`), which tears two vermilion halves apart on first load and then once every seven days (`localStorage`).
  Carries a soft drop shadow by default (`shadow: false` to drop it) — it gives the tip-over weight and stops the
  rotating chevron from shimmering at small render sizes.
- `initLoader(el, { mode, size })` — a compact looping indicator (default `pendulum`). Drives the **"Run Analysis"**
  button glyph (`<RunButton>`).

Both paint with `currentColor`, are theme-aware, and honour `prefers-reduced-motion` (resting A, no motion). On the
splash the mark takes `--surface-2`, not `--on-accent`: white on the light vermilion, charcoal on the dark one.

---

## 14. Custom Utility Classes (`globals.css`)

| Class                      | Effect                                                                                                                                                                                                                                                                                    |
|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `.lattice-grid`            | Applies the engraved 30px grid background to any element                                                                                                                                                                                                                                  |
| `.seal`                    | The vermilion brand square (accent bg + 8px engraved grid + inner highlight) — used by `<Seal>`                                                                                                                                                                                           |
| `.accent-panel`            | Vermilion surface with a faint engraved grid (CTAs/heroes)                                                                                                                                                                                                                                |
| `.focus-ring`              | Sharp accent-soft focus outline helper                                                                                                                                                                                                                                                    |
| `.splash*`                 | The torn-paper boot screen (used by `<Splash>`)                                                                                                                                                                                                                                           |
| `.rvx` and children        | The MR Review workspace stylesheet (`.rvx-head`, `.rvx-body`, `.ftree`, `.diff`, `.thread`, `.fix`, `.rc`, `.pipe`, `.dd*`, …). A self-contained block at the bottom of `globals.css` that the `/review` page emits class names against. Uses the Lattice tokens, so it is theme-aware.   |
| `.af-*` and `.seg` / `.sw` | The New Analysis creation-flow stylesheet (`.af-grid`, `.af-step`, `.field`, `.inp`/`.ta`, `.pp` persona tiles, `.mr-parsed`, `.rev-chip`, `.ready-box`, `.sum-card`) plus the shared segmented control (`.seg`) and toggle switch (`.sw`). The `/analyze` page emits these. Theme-aware. |

---

## 15. Views & Routes

The app mirrors the Lattice reference. Each nav item is a real route under `src/app/(app)/`:

| Route       | View                | Notes                                                                                                                                                                                                                                          |
|-------------|---------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/`         | Home                | Welcome, 4-up stats, merge queue, productivity bar chart, side rail                                                                                                                                                                            |
| `/queue`    | Merge Queue         | Filter tabs, MR rows with a state machine, role-aware actions, drag-reorder, collapsible groups                                                                                                                                                |
| `/analyze`  | New Analysis        | The creation flow (5 steps: scope · context · personas · reviewers · visibility) + a sticky live summary. Reached from the sidebar **Run Analysis** button and the Queue's **New analysis** CTA. Uses the `.af-*` stylesheet in `globals.css`. |
| `/review`   | MR Review workspace | Full-bleed 3-column: file tree · diff + inline threads/fixes · rating & pipeline rail. Role toggle + state rail. (Layout drops page padding + status bar for this route.)                                                                      |
| `/personas` | Persona marketplace | Kanji-glyph persona cards on severity-colored squares                                                                                                                                                                                          |
| `/stats`    | Organisation insights | Organisation-wide reporting placeholder                                                                                                                                                                                                      |
| `/history`  | Analysis History    | Stat cards, filter tabs, runs table with persona/result pills                                                                                                                                                                                  |

Patterns to reuse when extending: page head = `<Overline>` + `font-display` H1 + muted sub; segmented filters =
`<Tabs variant="segment">`; stat rows = `<StatCard>`; data tables use mono `.th`-style headers with hairline row
borders; CSS bar charts use a fixed-height flex container with `items-end` and percentage-height bars (the bar must be a
full-height flex child so the percentage resolves).
