# Arnold UI — Agent Guidelines

Arnold is an AI-driven code review platform for GitLab on-prem. This is the frontend.

## Design System

**Read [`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md) before writing any UI code.** It is the canonical reference for colors,
typography, spacing, component patterns, and theming. `DESIGN_SYSTEM.md` is the implementation contract; [
`DESIGN.md`](DESIGN.md) is the creative intent.

The system is named **Lattice** (kami / 紙): a warm paper canvas under an engraved grid, a single vermilion signal
accent, sharp corners, and structural hairlines. It is **light-first with a dark theme**, toggled by `data-theme` on
`<html>`.

Key rules to internalize:

- **Hairline Rule**: structure is drawn with 1px hairline borders (`border-border` / `border-2`) on every card, row,
  input, and panel — plus the engraved lattice grid on the page canvas. (This is the inverse of the old "No-Line" rule.)
- **Sharp corners**: radius is `0` everywhere (`rounded-none`). Only avatars/dots/meters/count-pills are `rounded-full`.
- **Fonts**: Space Grotesk (`font-display`, headings/figures), system sans (body), IBM Plex Mono (`font-mono`,
  labels/IDs/status/code).
- **Accent**: vermilion `#E24329` (`text-accent` / `bg-accent`). One signal color. Never use standard blue for links —
  use `text-accent-ink`.
- **CTA buttons**: flat vermilion (no gradient). Use `<Button variant="primary">`.
- **Surfaces**: `bg` (canvas) → `surface` (cards) → `surface-2` (inputs/code), each separated by a hairline. No card
  shadows.
- **Eyebrows**: mono, uppercase, vermilion (`<Overline>`) head sections.
- **Theme-aware**: never hardcode hex; always use token utilities (`bg-surface`, `text-muted`, `text-accent`, …) so both
  themes work.

## Project Structure

```
src/
├── app/              # Next.js App Router layouts and pages
│   ├── layout.js     # Root layout (fonts, favicon, theme bootstrap, metadata)
│   ├── globals.css   # Lattice theme tokens + Tailwind v4 config + .rvx workspace CSS
│   └── (app)/        # Authed shell (AppLayout) + routes:
│       ├── page.js   #   /          Home
│       ├── queue/    #   /queue     Merge Queue (state machine, drag-reorder, groups)
│       ├── analyze/  #   /analyze   New Analysis (5-step creation flow) — reached via "Run Analysis"
│       ├── review/   #   /review    MR Review workspace (3-col diff/findings/rating)
│       ├── personas/ #   /personas  Persona marketplace
│       ├── stats/    #   /stats     Organisation insights (coming soon)
│       └── history/  #   /history   Analysis History (runs table)
├── components/
│   ├── ui/           # Primitives (Seal, Button, Badge, Input, Card, Avatar, StatCard…)
│   ├── layout/       # Shells (Sidebar, TopNav, AppShell, AppLayout, StatusBar,
│   │                 #   ThemeToggle, Splash, RunButton)
│   ├── composite/    # Domain components (MRListItem, RuleCard, DiffViewer, etc.)
│   └── icons/        # Custom SVG icons
├── lib/              # Shared helpers — api.js (NEXT_PUBLIC_API_URL), arnold-mark.js (A animations)
└── ...
res/                  # JSON schema contracts backing agent workflows
public/               # Static assets — arnold-mark.svg (seal glyph), favicon.svg
config/               # Environment-specific YAML configs
server.js             # Express server: sign-in (auth.js) + API proxy
.design-reference/    # (gitignored) The Lattice reference HTML — source of truth for view markup
```

## Build & Development

| Command         | Purpose                                                     |
|-----------------|-------------------------------------------------------------|
| `pnpm dev`      | Turbopack dev server on `http://localhost:3000/`            |
| `pnpm build`    | Production bundle; run before release                       |
| `pnpm start`    | Serve the built app                                         |
| `pnpm lint`     | ESLint with `next/core-web-vitals`; must be clean pre-PR    |

## Tech Stack

- **React** (JavaScript + JSX). Do not use TypeScript.
- **Tailwind CSS v4** for styling. Theme tokens defined in `globals.css` via `@theme inline`.
- **Next.js App Router** for routing and server components.
- **Lucide React** for icons.
- **Space Grotesk / IBM Plex Mono** loaded from bundled files via `next/font/local` in `layout.js` (body uses the native system sans
  stack).
- **auth.js** for sign-in (GitHub and OpenID Connect providers; dependency-free).
- **No external state management** (no Redux, MobX, Zustand). Use `useState`/`useEffect` only.
- **No external component libraries** beyond React, Tailwind, and lucide-react.

## Coding Conventions

### General

- 2-space indentation, trailing semicolons, single quotes.
- Functional React components with explicit `'use client'` boundaries.
- Components in PascalCase, helpers in camelCase.
- Store multi-use logic in `src/lib`; colocate feature-specific hooks beside their component.

### Component Conventions

- One component per file, PascalCase filename (e.g., `Badge.jsx`).
- Named exports only.
- Every component accepts a `className` prop; spread `...props` onto the root element.
- Props: `children`, `className`, `variant`, `size` where relevant.
- Accessibility: `role`/`aria-label` where appropriate, `focus-visible` ring for focus states.
- Mobile-first: base styles for mobile, add `sm`/`md`/`lg` utilities as needed.
- No inline styles — use Tailwind classes exclusively.

### Styling Rules (from DESIGN_SYSTEM.md)

- Use hairline borders (`border border-border`, or `border-2` for stronger separation) for structure on
  cards/rows/inputs/panels. Keep the main canvas transparent so the lattice grid shows.
- Use `font-display` (Space Grotesk) for headings/figures, `font-mono` (IBM Plex Mono) for labels/IDs/status/code, and
  the default body font for prose.
- Use `rounded-none` (sharp) for everything except avatars/dots/meters/count-pills (`rounded-full`).
- Use `<Button variant="primary">` (flat vermilion) for primary CTAs; `secondary`/`outline` are hairline-bordered.
- Use the `.accent-panel` utility for vermilion hero/CTA surfaces and `.lattice-grid` to apply the engraved grid.
- Links always use `text-accent-ink` — never standard blue (blue is reserved for `sev-maint`).
- Use the existing component library in `src/components/ui/` before creating new primitives. Prefer the `bg`/`surface`/
  `surface-2`, `fg`/`muted`/`faint`, `border`, `accent` tokens over the legacy `surface-container-*` / `on-surface-*`
  aliases.

## Authentication

Handled by `auth.js` and wired in `server.js`. Sign-in issues an http-only session JWT that the `/api` proxy forwards to
the backend as a bearer token. See the Authentication section of [README.md](README.md).

## Testing

- Automated tests are not yet scaffolded; add React Testing Library + Vitest when introducing coverage.
- Name files `<Component>.test.jsx`, colocate under `src/components/__tests__/`.
- Document manual verification steps in PRs until automated suites exist.

## Configuration & API

- Set `NEXT_PUBLIC_API_URL` in `.env.local`; defaults to `http://localhost:8090`.
- When APIs evolve, update `src/lib/api.js` and synchronize `res/*-jsonschema.json`.
