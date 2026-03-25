# Arnold AI — Design System

> The canonical reference for building consistent, high-quality UI across the Arnold code-review platform.
> All LLMs generating UI code for this project **must** follow this document.
> Derived from the Stitch-generated spec in `DESIGN.md`.

---

## 1. Brand Identity

| Property | Value |
|----------|-------|
| Product name | **Arnold AI** |
| Tagline | THE SENTIENT LEDGER |
| Creative North Star | "The Sentient Code Reviewer" |
| Personality | Premium, data-dense, editorial, spacecraft glass-cockpit |
| Visual tone | Deep tonal layering, intentional asymmetry, no visual noise |

---

## 2. Color Palette

Arnold uses a **dark-first** theme built on tonal surface layering. All colors are defined as CSS custom properties in `globals.css` and mapped to Tailwind tokens via `@theme inline`.

### 2.1 Surface Hierarchy (Tonal Layering)

Depth is created by stepping through the surface scale — **never** with 1px borders.

| Token | Hex | Usage |
|-------|-----|-------|
| `--surface` | `#121316` | Base page background |
| `--surface-container-lowest` | `#0d0d10` | Deepest inset (diff gutters) |
| `--surface-container-low` | `#1b1b1f` | Sidebar, nav bar |
| `--surface-container` | `#1f1f23` | Main content cards |
| `--surface-container-high` | `#292a2d` | Active panels, code diffs, hover states |
| `--surface-container-highest` | `#333438` | Inputs, elevated callouts |

### 2.2 On-Surface (Text)

| Token | Hex | Usage |
|-------|-----|-------|
| `--on-surface` | `#E2E4E9` | Primary text |
| `--on-surface-variant` | `#c4c6cf` | Code text, secondary body |
| `--on-surface-muted` | `#8B919E` | Helper text, labels |
| `--on-surface-faint` | `#555C6B` | Disabled, placeholder |

### 2.3 Primary (Intelligence Accent)

The terracotta-leaning tone represents the AI's human-like intuition.

| Token | Hex | Usage |
|-------|-----|-------|
| `--primary` | `#ffb4a6` | Links, active tabs, focus rings, status "Ready" dot |
| `--primary-container` | `#d63b22` | CTA gradient end, error-like containers |
| `--on-primary` | `#121316` | Text on primary surfaces |

**CTA Gradient:** `linear-gradient(135deg, #ffb4a6, #d63b22)` — gives AI interaction points a "living" energy.

### 2.4 Secondary

| Token | Hex | Usage |
|-------|-----|-------|
| `--secondary` | `#c4c6cf` | Secondary text emphasis |
| `--secondary-container` | `#29292C` | Secondary action backgrounds |

### 2.5 Tertiary (Gold/Amber — AI Analyzing)

| Token | Hex | Usage |
|-------|-----|-------|
| `--tertiary` | `#faba75` | Analyzing state glow, confidence scores |
| `--tertiary-container` | `#9d6a2c` | Intelligence badge backgrounds |

### 2.6 Error

| Token | Hex | Usage |
|-------|-----|-------|
| `--error` | `#ffb4ab` | Issue found, critical alerts |
| `--error-container` | `#93000a` | Diff removal highlights |

### 2.7 Semantic Status

| Token | Hex | Usage |
|-------|-----|-------|
| `--status-success` | `#3DD6B5` | Ready, complete, passing |
| `--status-warning` | `#faba75` | Analyzing, caution (= tertiary) |
| `--status-error` | `#ffb4ab` | Errors, critical (= error) |
| `--status-info` | `#60A5FA` | In-progress, informational |
| `--status-neutral` | `#8B919E` | Disabled, legacy |

### 2.8 Outline (Ghost Border Fallback)

| Token | Value | Usage |
|-------|-------|-------|
| `--outline-variant` | `rgba(196, 198, 207, 0.15)` | Accessibility fallback when a boundary is needed |

Applied via the `.ghost-border` utility class: `box-shadow: inset 0 0 0 1px var(--outline-variant)`.

---

## 3. The "No-Line" Rule

> **1px solid borders for sectioning are strictly prohibited.**

Layout boundaries are defined through **background color shifts** (surface tonal steps). This eliminates visual noise and creates a premium editorial feel.

**How to separate sections:**
- Sidebar vs content: `surface-container-low` vs `surface`
- Card on page: `surface-container` on `surface`
- Nested panel: `surface-container-high` inside `surface-container`
- Code blocks: `surface-container-highest` for inputs/code

**Exceptions:**
- Left accent borders on AnalysisCards (4px, colored) — these are intentional visual markers, not structural lines
- The `.ghost-border` fallback at 15% opacity when accessibility demands it
- Focus rings (2px ring around interactive elements)

---

## 4. Typography

### 4.1 Font Stack (Dual-font strategy)

| Role | Font | CSS variable | Tailwind class |
|------|------|-------------|---------------|
| Headlines | **Manrope** | `--font-manrope` | `font-headline` |
| Body / Labels | **Inter** | `--font-inter` | `font-body` |
| Code | **JetBrains Mono** | `--font-jetbrains-mono` | `font-code` |

### 4.2 Type Scale

| Token | Font | Size | Weight | Usage |
|-------|------|------|--------|-------|
| `display-lg` | Manrope | 2rem (32px) | 700 | Page titles, sparse tracking (-2%) |
| `headline-sm` | Manrope | 1.5rem (24px) | 600 | Major section headers, file headers |
| `title-md` | Inter | 1.125rem (18px) | 500 | Repo names, PR titles |
| `body-md` | Inter | 0.875rem (14px) | 400 | Comments, documentation, body text |
| `body-sm` | Inter | 0.8125rem (13px) | 400 | Descriptions, helper text |
| `label-sm` | Inter | 0.6875rem (11px) | 600 | Metadata badges, uppercase labels (`tracking-widest`) |
| `code` | JetBrains Mono | 0.875rem (14px) | 400 | Code blocks, diffs (line-height 1.6) |

### 4.3 Typography Rules

- **Uppercase labels** use `label-sm` + `tracking-widest` + `uppercase` + `font-semibold` + `font-body`
- **Large stat numbers** use `display-lg` or `text-2xl font-bold font-headline`
- **Accent text** (e.g., "Architect" in greeting) uses `text-primary`
- **Links** use `text-primary` — **never** standard blue. Use `primary-fixed-dim` (#ffb4a6)
- **Code** uses `font-code` at 0.875rem with line-height 1.6

---

## 5. Elevation & Depth

Depth is a **functional tool**, not a stylistic choice.

### 5.1 Tonal Layering (Primary Method)

To "lift" a component, move it one step up the surface scale:
- Page → `surface`
- Sidebar/Nav → `surface-container-low`
- Cards → `surface-container`
- Active panels → `surface-container-high`
- Inputs, elevated callouts → `surface-container-highest`

### 5.2 Glassmorphism (Floating Elements)

For Intelligence badges, AI modals, and floating indicators:
```css
.glass {
  background: rgba(31, 31, 35, 0.7);
  backdrop-filter: blur(12px);
}
```

### 5.3 Ambient Shadows (Modals/Menus Only)

```css
box-shadow: 0 24px 48px rgba(0, 0, 0, 0.4);
```

Use only for floating menus and modals. Never for cards — use tonal shifts instead.

---

## 6. Spacing

Use Tailwind's default spacing scale. Preferred values:

| Token | Value | Usage |
|-------|-------|-------|
| `1` | 4px | Tight icon gaps |
| `1.5` | 6px | Badge padding-y |
| `2` | 8px | Inline spacing, badge padding-x |
| `3` | 12px | Small card padding |
| `4` | 16px | Default gaps |
| `5` | 20px | Card content padding (preferred over 4 for major areas) |
| `6` | 24px | Section sub-spacing |
| `8` | 32px | Section spacing |
| `12` | 48px | Major section gaps |

---

## 7. Layout Structure

```
┌─────────────────────────────────────────────────────┐
│ Top Navigation (h-14, sticky, surface-container-low)│
├──────────────┬──────────────────────────────────────┤
│ Sidebar      │ Main Content Area                    │
│ (w-56)       │ (flex-1, p-6, surface)               │
│ surface-     │                                      │
│ container-   │  ┌─ surface-container ─────────────┐ │
│ low          │  │ Cards & panels                  │ │
│              │  └─────────────────────────────────┘ │
├──────────────┤                                      │
│ CTA button   ├──────────────────────────────────────┤
│ Bottom links │ Status Bar (h-10, surface-cont.-low) │
└──────────────┴──────────────────────────────────────┘
```

---

## 8. Border Radius

| Token | Value | Usage |
|-------|-------|-------|
| `rounded-sm` | 2px (0.125rem) | Buttons, badges, inputs, checkboxes — sharp/technical |
| `rounded-lg` | 8px | Cards, panels |
| `rounded-full` | 9999px | Avatars, status dots |

Per DESIGN.md, prefer **sharp corners** (`rounded-sm`) for interactive controls to maintain the "pro" technical appearance.

---

## 9. Iconography

### 9.1 Library

Use **Lucide React** (`lucide-react`).

### 9.2 Icon Sizes

| Context | Size | Tailwind class |
|---------|------|---------------|
| Inline with text | 16px | `w-4 h-4` |
| Nav items | 18px | `w-[18px] h-[18px]` |
| Card headers | 20px | `w-5 h-5` |
| Feature icons | 24px | `w-6 h-6` |
| Hero/empty states | 32-48px | `w-8 h-8` to `w-12 h-12` |

---

## 10. Component Patterns

### 10.1 File Structure

```
src/components/
├── ui/           # Primitives (Button, Badge, Input, Card, Avatar, etc.)
├── layout/       # Layout shells (Sidebar, TopNav, Container, StatusBar, AppShell)
├── composite/    # Domain-specific composed components
└── icons/        # Custom SVG icons (Arnold logo, maze mark)
```

### 10.2 Component API

```jsx
export function ComponentName({
  variant = 'default',
  size = 'md',
  className = '',
  children,
  ...props
}) { /* ... */ }
```

### 10.3 Button Variants

| Variant | Styling |
|---------|---------|
| `primary` | CTA gradient (135deg, primary → primary-container) |
| `secondary` | `secondary-container` bg, `secondary` text |
| `inverted` | `on-surface` bg, `surface` text |
| `outline` | `.ghost-border`, transparent bg |
| `ghost` | Transparent, muted text, surface-container on hover |
| `danger` | `error-container` bg, `error` text |
| `link` | `primary` text, underline on hover |

### 10.4 Input Styling

Inputs are **flush with background** using `surface-container-highest`. Focus is indicated by a `2px bottom-only` highlight in `primary` — no surrounding border.

### 10.5 Diff Viewer

- **No vertical or horizontal lines** between rows
- Use `surface-container-low` background on row hover
- Removals: `error-container` at 30% opacity background
- Text remains `on-surface-variant`

---

## 11. Interactive States

### 11.1 Focus

```
focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-surface
```

### 11.2 Hover

- **Buttons**: Darken/lighten by ~10%
- **Cards**: Step up one surface level (`surface-container` → `surface-container-high`)
- **Links**: Underline appears
- **Table rows**: `surface-container-low` background

### 11.3 Active/Selected

- **Nav items**: `primary` text + `primary/10` background
- **Tabs**: Underline with `primary` + `on-surface` text

### 11.4 Agent Status Indicators (per DESIGN.md)

- **Analyzing**: Pulsing `tertiary` (#faba75) glow (`animate-pulse`)
- **Ready**: Static `primary` (#ffb4a6) dot
- **Issue Found**: Sharp `error` (#ffb4ab) accent

### 11.5 Disabled

```
disabled:pointer-events-none disabled:opacity-50
```

---

## 12. Do's and Don'ts

### Do
- Use **tonal surface shifts** to create hierarchy — never 1px borders
- Use the CTA gradient for primary action buttons
- Use `secondary-container` for secondary actions
- Use `whitespace` (spacing 16 or 20) to separate major modules
- Use `font-headline` (Manrope) for headings, `font-body` (Inter) for body, `font-code` (JetBrains Mono) for code
- Use `primary` (#ffb4a6) for links — never standard blue
- Use glassmorphism (`.glass`) for floating AI elements
- Use ambient shadows (`0 24px 48px rgba(0,0,0,0.4)`) only for modals/menus

### Don't
- Don't use **100% opaque borders** — they create visual noise
- Don't use standard blue for links
- Don't use heavy drop shadows on cards — rely on surface shifts
- Don't use TypeScript — this project is JavaScript/JSX only
- Don't use inline styles — rely on Tailwind classes
- Don't import external component libraries beyond React, Tailwind, and lucide-react
- Don't use light theme colors — Arnold is dark-first

---

## 13. Utility Classes (Custom)

| Class | Effect |
|-------|--------|
| `.glass` | Glassmorphism: semi-transparent bg + 12px backdrop-blur |
| `.cta-gradient` | Primary CTA gradient (135deg, primary → primary-container) |
| `.ghost-border` | 1px inset shadow at 15% opacity (accessibility fallback) |
