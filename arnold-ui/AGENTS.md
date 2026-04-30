<!-- BEGIN:nextjs-agent-rules -->
# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.
<!-- END:nextjs-agent-rules -->

# Arnold UI — Agent Guidelines

Arnold is an AI-driven code review platform for GitLab on-prem. This is the frontend.

## Design System

**Read [`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md) before writing any UI code.** It is the canonical reference for colors, typography, spacing, component patterns, and the tonal layering philosophy.

The design system is derived from the Stitch-generated spec in [`DESIGN.md`](DESIGN.md). When in doubt, `DESIGN_SYSTEM.md` is the implementation reference and `DESIGN.md` is the creative intent.

Key rules to internalize:
- **No-Line Rule**: 1px solid borders are prohibited. Use surface tonal shifts for separation.
- **Fonts**: Manrope (headlines), Inter (body/labels), JetBrains Mono (code).
- **Primary accent**: `#ffb4a6` (terracotta). Never use standard blue for links.
- **CTA buttons**: Use the 135-degree gradient (`primary` → `primary-container`).
- **Elevation**: Tonal layering via the `surface-container-*` scale. No card shadows.
- **Glassmorphism**: For floating AI elements (`.glass` utility).

## Project Structure

```
src/
├── app/              # Next.js App Router layouts and pages
│   ├── layout.js     # Root layout (fonts, metadata)
│   ├── globals.css   # Theme tokens + Tailwind v4 config
│   └── page.js       # Home route
├── components/
│   ├── ui/           # Primitives (Button, Badge, Input, Card, Avatar, etc.)
│   ├── layout/       # Shells (Sidebar, TopNav, AppShell, Container, StatusBar)
│   ├── composite/    # Domain components (MRListItem, RuleCard, DiffViewer, etc.)
│   └── icons/        # Custom SVG icons
├── lib/              # Shared helpers, API surface (api.js reads NEXT_PUBLIC_API_URL)
└── ...
res/                  # JSON schema contracts backing agent workflows
public/               # Static assets (icons, fonts, downloads)
config/               # Environment-specific YAML configs
server.js             # Express server with Olympus auth + API proxy
```

## Build & Development

| Command | Purpose |
|---------|---------|
| `npm run dev` | Turbopack dev server on `http://localhost:3000` |
| `npm run build` | Production bundle; run before release |
| `npm run start` | Serve the built app |
| `npm run lint` | ESLint with `next/core-web-vitals`; must be clean pre-PR |

## Tech Stack

- **React** (JavaScript + JSX). Do not use TypeScript.
- **Tailwind CSS v4** for styling. Theme tokens defined in `globals.css` via `@theme inline`.
- **Next.js App Router** for routing and server components.
- **Lucide React** for icons.
- **Manrope / Inter / JetBrains Mono** loaded via `next/font/google` in `layout.js`.
- **Olympus** for authentication middleware (implemented in `server.js`; see [OlympusIntegration.md](OlympusIntegration.md)).
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
- Use the `surface-container-*` scale for backgrounds — not borders.
- Use `font-headline`, `font-body`, `font-code` for the correct typeface.
- Use `rounded-sm` for buttons/badges/inputs (sharp/technical), `rounded-lg` for cards.
- Use `.cta-gradient` class for primary CTAs.
- Use `.ghost-border` class when accessibility requires a visible boundary.
- Links always use `text-primary` (`#ffb4a6`) — never standard blue.
- Use the existing component library in `src/components/ui/` before creating new primitives.

## Authentication

Handled via Olympus in `server.js`. See [OlympusIntegration.md](OlympusIntegration.md) for details. Middleware: `withHumanUserToken`, `isAuthenticated`.

## Testing

- Automated tests are not yet scaffolded; add React Testing Library + Vitest when introducing coverage.
- Name files `<Component>.test.jsx`, colocate under `src/components/__tests__/`.
- Document manual verification steps in PRs until automated suites exist.

## Commit & PR Guidelines

- Sentence-case, action-oriented commit messages (`Added execution stats page`). Subject under 72 chars.
- Squash only when simplifying your branch; never rewrite shared history.
- PRs must outline impact, validation (`npm run lint`, manual flows), and attach UI screenshots.
- Call out environment or schema changes explicitly.

## Configuration & API

- Set `NEXT_PUBLIC_API_URL` in `.env.local`; defaults to `http://localhost:1900`.
- When APIs evolve, update `src/lib/api.js` and synchronize `res/*-jsonschema.json`.
