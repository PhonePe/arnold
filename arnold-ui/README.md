# Arnold UI

Frontend for **Arnold AI** — an AI-driven code review platform for GitLab on-prem.

## Quick Start

```bash
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

## Scripts

| Command | Purpose |
|---------|---------|
| `npm run dev` | Start Turbopack dev server |
| `npm run build` | Production build |
| `npm run start` | Serve production build |
| `npm run lint` | Run ESLint |

## Tech Stack

- **Next.js 16** (App Router)
- **React 19** (JavaScript/JSX — no TypeScript)
- **Tailwind CSS v4**
- **Lucide React** for icons
- **Manrope** / **Inter** / **JetBrains Mono** fonts

## Project Structure

```
src/
├── app/              # App Router pages and layouts
├── components/
│   ├── ui/           # Primitives (Button, Badge, Card, Input, Avatar, etc.)
│   ├── layout/       # Shells (Sidebar, TopNav, AppShell, StatusBar)
│   └── composite/    # Domain components (MRListItem, RuleCard, DiffViewer)
└── lib/              # Shared helpers and API utilities
```

## Design System

See [`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md) for colors, typography, component patterns, and the tonal layering philosophy. The upstream creative spec lives in [`DESIGN.md`](DESIGN.md).

## Authentication

Olympus SSO via Express middleware in `server.js`. See [OlympusIntegration.md](OlympusIntegration.md).

## Configuration

Set `NEXT_PUBLIC_API_URL` in `.env.local` to point at the Arnold backend. Defaults to `http://localhost:1900`.
